#Requires -Version 7.0
<#
.SYNOPSIS
  Drives the real app over a JSON-line protocol on stdin/stdout (AI pilot screen driver).
.DESCRIPTION
  Runs only on GitHub Actions. It prepares the same black desktop as record.ps1, starts the app, waits for
  its HUD, writes the ready line and then answers one request per stdin line until EOF.
  stdout carries only protocol JSON; diagnostics go to stderr. Requests: screenshot, click, move, drag,
  type, key, wait. Coordinates are capture pixels, 0-based on the virtual screen.
.PARAMETER Exe
  Path to 'Usage Monitor.exe' (installed or built).
.PARAMETER DataDir
  Passed to the app as --user-data-dir (seed.ps1 output).
.PARAMETER OutDir
  Output folder: shots\NNN.jpg for each screenshot, app.log copied at the end.
.PARAMETER StartupSeconds
  Longest wait for a first visible frame (400 changed pixels). Default 45; past it the driver answers not-visible.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Exe,
    [Parameter(Mandatory)][string]$DataDir,
    [Parameter(Mandatory)][string]$OutDir,
    [int]$StartupSeconds = 45
)

$ErrorActionPreference = 'Stop'

if ($env:GITHUB_ACTIONS -ne 'true') {
    throw 'screen-driver.ps1 changes the desktop and only runs when GITHUB_ACTIONS is true.'
}
if (-not (Test-Path -LiteralPath $Exe)) { throw "Exe not found: $Exe" }
if ([IntPtr]::Size -ne 8) { throw 'screen-driver.ps1 needs 64-bit PowerShell: the INPUT layout is 40 bytes.' }

Add-Type -AssemblyName System.Drawing.Common, System.Windows.Forms

# Native helpers. Types only: nothing here is called until the main section below.
# The System.Private.Windows.* assemblies only exist in the newer .NET that pwsh may carry.
$references = @('System.Drawing.Common', 'System.Windows.Forms', 'System.Drawing.Primitives', 'System.Collections')
$references += @('System.Private.Windows.GdiPlus', 'System.Private.Windows.Core') |
    Where-Object { Test-Path -LiteralPath (Join-Path $PSHOME "$_.dll") }
Add-Type -ReferencedAssemblies $references -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Forms;

public static class ScreenDriverNative {
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll", SetLastError = true)] public static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [StructLayout(LayoutKind.Sequential)] public struct MOUSEINPUT { public int dx; public int dy; public uint mouseData; public uint dwFlags; public uint time; public UIntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public UIntPtr dwExtraInfo; }

    // 40 bytes on 64-bit Windows: type at offset 0, the union (mouse or keyboard) at offset 8.
    [StructLayout(LayoutKind.Explicit, Size = 40)] public struct INPUT {
        [FieldOffset(0)] public uint type;
        [FieldOffset(8)] public MOUSEINPUT mi;
        [FieldOffset(8)] public KEYBDINPUT ki;
    }

    static bool Send(INPUT input) {
        return SendInput(1, new INPUT[] { input }, Marshal.SizeOf(typeof(INPUT))) == 1;
    }

    // Moves the pointer to (x, y) in screen pixels with an absolute SendInput mouse move.
    public static bool MoveTo(int x, int y, Rectangle vs) {
        INPUT input = new INPUT();
        input.type = 0; // INPUT_MOUSE
        input.mi.dx = (int)((x - vs.X) * 65535L / Math.Max(1, vs.Width - 1));
        input.mi.dy = (int)((y - vs.Y) * 65535L / Math.Max(1, vs.Height - 1));
        input.mi.dwFlags = 0x0001 | 0x4000 | 0x8000; // MOUSEEVENTF_MOVE | MOUSEEVENTF_VIRTUALDESK | MOUSEEVENTF_ABSOLUTE
        return Send(input);
    }

    // Presses (down) or releases the left button at the current pointer position.
    public static bool Button(bool down) {
        INPUT input = new INPUT();
        input.type = 0; // INPUT_MOUSE
        input.mi.dwFlags = down ? 0x0002u : 0x0004u; // MOUSEEVENTF_LEFTDOWN : MOUSEEVENTF_LEFTUP
        return Send(input);
    }

    // Virtual-key press or release. Extended keys (arrows, home, end, delete) need the extended flag.
    public static bool VirtualKey(ushort vk, bool extended, bool up) {
        INPUT input = new INPUT();
        input.type = 1; // INPUT_KEYBOARD
        input.ki.wVk = vk;
        input.ki.dwFlags = (extended ? 0x0001u : 0u) | (up ? 0x0002u : 0u); // KEYEVENTF_EXTENDEDKEY | KEYEVENTF_KEYUP
        return Send(input);
    }

    // One UTF-16 code unit as typed text (KEYEVENTF_UNICODE, wVk stays 0).
    public static bool UnicodeKey(ushort unit, bool up) {
        INPUT input = new INPUT();
        input.type = 1; // INPUT_KEYBOARD
        input.ki.wScan = unit;
        input.ki.dwFlags = 0x0004u | (up ? 0x0002u : 0u); // KEYEVENTF_UNICODE | KEYEVENTF_KEYUP
        return Send(input);
    }

    public static Rectangle VirtualScreen() {
        return SystemInformation.VirtualScreen;
    }

    // Copies the virtual screen into 32bpp BGRA bytes (stride * height).
    public static byte[] Capture(out int width, out int height, out int stride) {
        Rectangle r = SystemInformation.VirtualScreen;
        width = r.Width;
        height = r.Height;
        using (Bitmap bmp = new Bitmap(r.Width, r.Height, PixelFormat.Format32bppArgb)) {
            using (Graphics g = Graphics.FromImage(bmp)) {
                g.CopyFromScreen(r.X, r.Y, 0, 0, r.Size, CopyPixelOperation.SourceCopy);
            }
            BitmapData data = bmp.LockBits(new Rectangle(0, 0, r.Width, r.Height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            try {
                stride = data.Stride;
                byte[] pixels = new byte[stride * r.Height];
                Marshal.Copy(data.Scan0, pixels, 0, pixels.Length);
                return pixels;
            } finally {
                bmp.UnlockBits(data);
            }
        }
    }

    // Counts pixels that differ from the baseline by more than 'tolerance' in any channel; x,y,w,h is their box.
    public static int Diff(byte[] now, byte[] baseline, int width, int height, int stride, int tolerance, out int x, out int y, out int w, out int h) {
        int count = 0, minX = int.MaxValue, minY = int.MaxValue, maxX = -1, maxY = -1;
        for (int py = 0; py < height; py++) {
            int row = py * stride;
            for (int px = 0; px < width; px++) {
                int i = row + px * 4;
                if (Math.Abs(now[i] - baseline[i]) > tolerance || Math.Abs(now[i + 1] - baseline[i + 1]) > tolerance || Math.Abs(now[i + 2] - baseline[i + 2]) > tolerance) {
                    count++;
                    if (px < minX) minX = px;
                    if (py < minY) minY = py;
                    if (px > maxX) maxX = px;
                    if (py > maxY) maxY = py;
                }
            }
        }
        if (count == 0) { x = 0; y = 0; w = 0; h = 0; return 0; }
        x = minX; y = minY; w = maxX - minX + 1; h = maxY - minY + 1;
        return count;
    }

    public static Bitmap ToBitmap(byte[] pixels, int width, int height, int stride) {
        Bitmap bmp = new Bitmap(width, height, PixelFormat.Format32bppArgb);
        BitmapData data = bmp.LockBits(new Rectangle(0, 0, width, height), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        try {
            for (int py = 0; py < height; py++) {
                Marshal.Copy(pixels, py * stride, IntPtr.Add(data.Scan0, py * data.Stride), width * 4);
            }
        } finally {
            bmp.UnlockBits(data);
        }
        return bmp;
    }

    // JPEG bytes at the given quality (GDI+ default is 75; the protocol asks for about 85).
    public static byte[] EncodeJpeg(byte[] pixels, int width, int height, int stride, long quality) {
        using (Bitmap bmp = ToBitmap(pixels, width, height, stride))
        using (MemoryStream ms = new MemoryStream()) {
            ImageCodecInfo codec = null;
            foreach (ImageCodecInfo candidate in ImageCodecInfo.GetImageEncoders()) {
                if (candidate.FormatID == ImageFormat.Jpeg.Guid) { codec = candidate; break; }
            }
            using (EncoderParameters parameters = new EncoderParameters(1)) {
                parameters.Param[0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, quality);
                bmp.Save(ms, codec, parameters);
            }
            return ms.ToArray();
        }
    }
}

public static class DesktopSetup {
    [DllImport("user32.dll", SetLastError = true)] public static extern bool SystemParametersInfo(uint uiAction, uint uiParam, string pvParam, uint fWinIni);
    [DllImport("user32.dll")] public static extern bool SetSysColors(int cElements, int[] lpaElements, int[] lpaRgbValues);
}

// Progman and WorkerW hold the wallpaper, so they are never hidden.
public static class DesktopWindows {
    public delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc callback, IntPtr lParam);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetClassName(IntPtr hWnd, StringBuilder text, int max);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int max);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern IntPtr FindWindow(string cls, string title);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern IntPtr FindWindowEx(IntPtr parent, IntPtr after, string cls, string title);

    static string ClassOf(IntPtr hWnd) { StringBuilder sb = new StringBuilder(256); GetClassName(hWnd, sb, sb.Capacity); return sb.ToString(); }
    static string TitleOf(IntPtr hWnd) { StringBuilder sb = new StringBuilder(512); GetWindowText(hWnd, sb, sb.Capacity); return sb.ToString(); }

    // Hides every visible top-level window except Progman and WorkerW. Returns "class|title" for each.
    public static List<string> HideTopLevel() {
        List<string> hidden = new List<string>();
        EnumWindows((hWnd, lParam) => {
            if (!IsWindowVisible(hWnd)) return true;
            string cls = ClassOf(hWnd);
            if (cls == "Progman" || cls == "WorkerW") return true;
            ShowWindow(hWnd, 0);
            hidden.Add(cls + "|" + TitleOf(hWnd));
            return true;
        }, IntPtr.Zero);
        return hidden;
    }

    // Hides SHELLDLL_DefView (desktop icons) under Progman and under every WorkerW.
    public static List<string> HideDesktopIcons() {
        List<IntPtr> parents = new List<IntPtr>();
        IntPtr progman = FindWindow("Progman", null);
        if (progman != IntPtr.Zero) parents.Add(progman);
        EnumWindows((hWnd, lParam) => { if (ClassOf(hWnd) == "WorkerW") parents.Add(hWnd); return true; }, IntPtr.Zero);
        List<string> hidden = new List<string>();
        foreach (IntPtr parent in parents) {
            IntPtr view = FindWindowEx(parent, IntPtr.Zero, "SHELLDLL_DefView", null);
            if (view == IntPtr.Zero) continue;
            ShowWindow(view, 0);
            hidden.Add("SHELLDLL_DefView|under " + ClassOf(parent));
        }
        return hidden;
    }
}
'@

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$shotsDir = Join-Path $OutDir 'shots'
New-Item -ItemType Directory -Force -Path $shotsDir | Out-Null

# Protocol channel: UTF-8 without BOM, one JSON object per line, flushed on every write.
$stdout = [System.IO.StreamWriter]::new([Console]::OpenStandardOutput(), [System.Text.UTF8Encoding]::new($false))
$stdout.AutoFlush = $true
$stdout.NewLine = "`n"
$stdin = [System.IO.StreamReader]::new([Console]::OpenStandardInput(), [System.Text.UTF8Encoding]::new($false))

# Key names of the protocol: virtual-key code and whether the key is an extended one.
$KEYS = @{
    'enter'     = @(0x0D, $false)
    'escape'    = @(0x1B, $false)
    'tab'       = @(0x09, $false)
    'pageup'    = @(0x21, $true)
    'space'     = @(0x20, $false)
    'up'        = @(0x26, $true)
    'down'      = @(0x28, $true)
    'left'      = @(0x25, $true)
    'right'     = @(0x27, $true)
    'home'      = @(0x24, $true)
    'end'       = @(0x23, $true)
    'pagedown'  = @(0x22, $true)
}

$script:shotIndex = 0
$script:buttonDown = $false
$script:lastPoint = $null
$script:vs = [System.Drawing.Rectangle]::Empty

function Write-Diag([string]$message) {
    [Console]::Error.WriteLine("[screen-driver] $message")
}

function Send-Json([hashtable]$payload) {
    $stdout.WriteLine((ConvertTo-Json -InputObject $payload -Compress))
}

function Get-IntField($request, [string]$name) {
    $value = $request.$name
    if ($null -eq $value) { throw "$name is missing" }
    # ConvertFrom-Json gives Int32/Int64 for whole numbers; 1.5, "1" and true are refused.
    if ($value -isnot [int] -and $value -isnot [long]) { throw "$name must be an integer" }
    [int]$value
}

# Capture coordinates to screen coordinates; anything outside the virtual screen is refused.
function Get-ScreenPoint([int]$x, [int]$y) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $script:vs.Width -or $y -ge $script:vs.Height) {
        throw "point outside the screen: $x,$y"
    }
    [pscustomobject]@{ X = $script:vs.X + $x; Y = $script:vs.Y + $y }
}

function Move-Pointer([int]$x, [int]$y) {
    $point = Get-ScreenPoint $x $y
    if (-not [ScreenDriverNative]::MoveTo($point.X, $point.Y, $script:vs)) { throw 'SendInput move failed' }
    $script:lastPoint = @($x, $y)
}

# Moves like a hand: 12 steps of 15 ms from the last point. A jump skips the enter and move events of what lies between.
function Move-Smooth([int]$x, [int]$y) {
    [void](Get-ScreenPoint $x $y)
    $from = $script:lastPoint
    if ($null -ne $from) {
        for ($step = 1; $step -lt 12; $step++) {
            Move-Pointer ([int][math]::Round($from[0] + ($x - $from[0]) * $step / 12)) ([int][math]::Round($from[1] + ($y - $from[1]) * $step / 12))
            Start-Sleep -Milliseconds 15
        }
    }
    Move-Pointer $x $y
}

function Set-Button([bool]$down) {
    if (-not [ScreenDriverNative]::Button($down)) { throw 'SendInput button failed' }
    $script:buttonDown = $down
}

function Invoke-Tool($request) {
    switch ([string]$request.tool) {
        'screenshot' {
            $w = 0; $h = 0; $stride = 0
            $pixels = [ScreenDriverNative]::Capture([ref]$w, [ref]$h, [ref]$stride)
            $jpeg = [ScreenDriverNative]::EncodeJpeg($pixels, $w, $h, $stride, 85)
            $script:shotIndex++
            [System.IO.File]::WriteAllBytes((Join-Path $shotsDir ('{0:D3}.jpg' -f $script:shotIndex)), $jpeg)
            return @{ image = [Convert]::ToBase64String($jpeg) }
        }
        'click' {
            Move-Smooth (Get-IntField $request 'x') (Get-IntField $request 'y')
            Start-Sleep -Milliseconds 120
            Set-Button $true
            Start-Sleep -Milliseconds 80
            Set-Button $false
        }
        'move' {
            Move-Smooth (Get-IntField $request 'x') (Get-IntField $request 'y')
        }
        'drag' {
            # All four fields are read and checked before the first pointer move.
            $fromX = Get-IntField $request 'x1'
            $fromY = Get-IntField $request 'y1'
            $toX = Get-IntField $request 'x2'
            $toY = Get-IntField $request 'y2'
            [void](Get-ScreenPoint $fromX $fromY)
            [void](Get-ScreenPoint $toX $toY)
            Move-Pointer $fromX $fromY
            Set-Button $true
            try {
                # The first event moves 1 px, as a real mouse does; then ten steps to the end (record.ps1 pattern).
                Move-Pointer ($fromX + [math]::Sign($toX - $fromX)) ($fromY + [math]::Sign($toY - $fromY))
                for ($step = 1; $step -le 10; $step++) {
                    Start-Sleep -Milliseconds 100
                    Move-Pointer ([int][math]::Round($fromX + ($toX - $fromX) * $step / 10)) ([int][math]::Round($fromY + ($toY - $fromY) * $step / 10))
                }
            } finally {
                Set-Button $false
            }
        }
        'type' {
            if ($null -eq $request.text) { throw 'text is missing' }
            foreach ($unit in ([string]$request.text).ToCharArray()) {
                if (-not [ScreenDriverNative]::UnicodeKey([uint16]$unit, $false)) { throw 'SendInput text failed' }
                if (-not [ScreenDriverNative]::UnicodeKey([uint16]$unit, $true)) { throw 'SendInput text failed' }
            }
        }
        'key' {
            if ($null -eq $request.name) { throw 'name is missing' }
            $name = [string]$request.name
            if (-not $KEYS.ContainsKey($name)) { throw "unknown key: $name" }
            $spec = $KEYS[$name]
            if (-not [ScreenDriverNative]::VirtualKey([uint16]$spec[0], [bool]$spec[1], $false)) { throw 'SendInput key failed' }
            if (-not [ScreenDriverNative]::VirtualKey([uint16]$spec[0], [bool]$spec[1], $true)) { throw 'SendInput key failed' }
        }
        'wait' {
            $ms = Get-IntField $request 'ms'
            if ($ms -lt 0 -or $ms -gt 10000) { throw "ms out of range 0..10000: $ms" }
            Start-Sleep -Milliseconds $ms
        }
        default { throw "unknown tool: $($request.tool)" }
    }
}

$proc = $null
$readySent = $false
$exitCode = 0
try {
    # 1. Black desktop: other top-level windows and desktop icons hidden, then a still baseline frame.
    [void][DesktopSetup]::SetSysColors(1, @(1), @(0))                          # COLOR_DESKTOP -> black
    [void][DesktopSetup]::SystemParametersInfo(0x14, 0, '', 0x01 -bor 0x02)    # SPI_SETDESKWALLPAPER, clear
    foreach ($entry in [DesktopWindows]::HideTopLevel()) { Write-Diag "hidden window $entry" }
    foreach ($entry in [DesktopWindows]::HideDesktopIcons()) { Write-Diag "hidden icons $entry" }
    Start-Sleep -Milliseconds 500

    $script:vs = [ScreenDriverNative]::VirtualScreen()
    [void][ScreenDriverNative]::SetCursorPos($script:vs.X + [int]($script:vs.Width / 2), $script:vs.Y + $script:vs.Height - 2)

    # 2. Wait for two captures 500 ms apart with no differing pixel, up to 10 s.
    $w = 0; $h = 0; $stride = 0
    $baseline = $null
    $lastChanged = -1
    $settle = [System.Diagnostics.Stopwatch]::StartNew()
    while ($settle.ElapsedMilliseconds -lt 10000) {
        $first = [ScreenDriverNative]::Capture([ref]$w, [ref]$h, [ref]$stride)
        Start-Sleep -Milliseconds 500
        $second = [ScreenDriverNative]::Capture([ref]$w, [ref]$h, [ref]$stride)
        $bx = 0; $by = 0; $bw = 0; $bh = 0
        $lastChanged = [ScreenDriverNative]::Diff($second, $first, $w, $h, $stride, 0, [ref]$bx, [ref]$by, [ref]$bw, [ref]$bh)
        if ($lastChanged -eq 0) { $baseline = $second; break }
    }
    if ($null -eq $baseline) { throw "desktop did not settle within 10 s: $lastChanged pixels still change" }

    # 3. Start the app and poll for the first visible frame (400 changed pixels, tolerance 8, as record.ps1).
    $clock = [System.Diagnostics.Stopwatch]::StartNew()
    $proc = Start-Process -FilePath $Exe -ArgumentList @('--user-data-dir', $DataDir) -PassThru
    $visible = $false
    while ($clock.Elapsed.TotalSeconds -lt $StartupSeconds) {
        if ($proc.HasExited) { Write-Diag 'app exited before the HUD was visible'; break }
        $frame = [ScreenDriverNative]::Capture([ref]$w, [ref]$h, [ref]$stride)
        $bx = 0; $by = 0; $bw = 0; $bh = 0
        $count = [ScreenDriverNative]::Diff($frame, $baseline, $w, $h, $stride, 8, [ref]$bx, [ref]$by, [ref]$bw, [ref]$bh)
        if ($count -ge 400) { $visible = $true; break }
        Start-Sleep -Milliseconds 100
    }

    if (-not $visible) {
        Send-Json -payload @{ ready = $false; error = 'not-visible' }
        $exitCode = 1
    }
    else {
        Start-Sleep -Seconds 3
        Send-Json -payload @{ ready = $true; width = $w; height = $h }
        $readySent = $true

        # 4. One request per line until EOF. A bad line is skipped; a failed action answers ok:false.
        while ($true) {
            $line = $stdin.ReadLine()
            if ($null -eq $line) { break }
            if ($line.Trim() -eq '') { continue }
            try {
                $request = ConvertFrom-Json -InputObject $line
            }
            catch {
                Write-Diag 'skipped a line that is not JSON'
                continue
            }
            $id = $request.id
            try {
                $result = Invoke-Tool $request
                # 300 ms after each action (not after a screenshot), so the app can repaint before the next request.
                if ([string]$request.tool -ne 'screenshot') { Start-Sleep -Milliseconds 300 }
                $response = @{ id = $id; ok = $true }
                if ($null -ne $result) { foreach ($key in $result.Keys) { $response[$key] = $result[$key] } }
            }
            catch {
                Write-Diag "request $id failed: $($_.Exception.Message)"
                $response = @{ id = $id; ok = $false; error = $_.Exception.Message }
            }
            Send-Json -payload $response
        }
    }
}
catch {
    Write-Diag "setup failed: $($_.Exception.Message)"
    if (-not $readySent) { Send-Json -payload @{ ready = $false; error = 'setup-failed' } }
    $exitCode = 1
}
finally {
    # Never leave the left button down, even after a failure in the middle of a drag.
    if ($script:buttonDown) {
        [void][ScreenDriverNative]::Button($false)
        $script:buttonDown = $false
        Write-Diag 'released the left button'
    }
    if ($null -ne $proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    $appLog = Join-Path (Join-Path $DataDir 'diagnostics') 'app.log'
    if (Test-Path -LiteralPath $appLog) { Copy-Item -LiteralPath $appLog -Destination (Join-Path $OutDir 'app.log') -Force }
    $stdout.Flush()
}
exit $exitCode

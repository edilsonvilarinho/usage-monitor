#Requires -Version 7.0
<#
.SYNOPSIS
  Records the virtual screen while the real app starts: frames.csv, JPEG frames, probe.json.
.DESCRIPTION
  Runs only on GitHub Actions: it hides every other top-level window and the desktop icons,
  then waits for a still desktop before the baseline. The pointer is parked at the bottom centre.
  Hover and drag scenarios send synthetic mouse input. frames.csv is t_ms,foreground,x,y,w,h; foreground = pixels that
  differ from the baseline (integer).
.PARAMETER Exe
  Path to the app exe ('Usage Monitor.exe': installed or built).
.PARAMETER DataDir
  Passed to the app as --user-data-dir.
.PARAMETER OutDir
  Output folder (frames.csv, frames/*.jpg, app.log, probe.json).
.PARAMETER Seconds
  Recording length.
.PARAMETER Scenario
  Same names as seed.ps1. From 12 s the pointer follows one plan built from the last non-empty box.
  Hover scenarios (docked-top-hover, docked-top-hole-hover) move onto it, leave to the bottom centre 3 s later
  and write events.json {enterMs, leaveMs}. floating-drag presses, drags (-200, +120) and releases, and writes
  {pressMs, releaseMs, dx, dy}. All times are on the t_ms clock; probe.json lists the executed pointer actions.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Exe,
    [Parameter(Mandatory)][string]$DataDir,
    [Parameter(Mandatory)][string]$OutDir,
    [int]$Seconds = 30,
    [ValidateSet('floating-idle', 'floating-drag', 'docked-top-idle', 'docked-bottom-idle', 'docked-left-idle', 'docked-right-idle', 'docked-top-hover', 'docked-top-hole-idle', 'docked-top-hole-hover')]
    [string]$Scenario = 'floating-idle'
)

$ErrorActionPreference = 'Stop'

if ($env:GITHUB_ACTIONS -ne 'true') {
    throw 'record.ps1 changes the desktop and only runs when GITHUB_ACTIONS is true.'
}
if (-not (Test-Path -LiteralPath $Exe)) { throw "Exe not found: $Exe" }

Add-Type -AssemblyName System.Drawing.Common, System.Windows.Forms

# Native helpers. Types only: nothing here is called until the recording section below.
# The System.Private.Windows.* assemblies only exist in the newer .NET that pwsh may carry.
$references = @('System.Drawing.Common', 'System.Windows.Forms', 'System.Drawing.Primitives')
$references += @('System.Private.Windows.GdiPlus', 'System.Private.Windows.Core') |
    Where-Object { Test-Path -LiteralPath (Join-Path $PSHOME "$_.dll") }
Add-Type -ReferencedAssemblies $references -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Forms;

public static class RealAppNative {
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll", SetLastError = true)] public static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [StructLayout(LayoutKind.Sequential)] public struct MOUSEINPUT { public int dx; public int dy; public uint mouseData; public uint dwFlags; public uint time; public UIntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Sequential)] public struct INPUT { public uint type; public MOUSEINPUT mi; }

    // Moves the pointer to (x, y) in virtual-screen pixels with an absolute SendInput mouse move.
    public static bool MoveTo(int x, int y, Rectangle vs) {
        INPUT input = new INPUT();
        input.type = 0; // INPUT_MOUSE
        input.mi.dx = (int)((x - vs.X) * 65535L / Math.Max(1, vs.Width - 1));
        input.mi.dy = (int)((y - vs.Y) * 65535L / Math.Max(1, vs.Height - 1));
        input.mi.dwFlags = 0x0001 | 0x4000 | 0x8000; // MOUSEEVENTF_MOVE | MOUSEEVENTF_VIRTUALDESK | MOUSEEVENTF_ABSOLUTE
        return SendInput(1, new INPUT[] { input }, Marshal.SizeOf(typeof(INPUT))) == 1;
    }

    // Presses (down) or releases the left button at the current pointer position.
    public static bool Button(bool down) {
        INPUT input = new INPUT();
        input.type = 0; // INPUT_MOUSE
        input.mi.dwFlags = down ? 0x0002u : 0x0004u; // MOUSEEVENTF_LEFTDOWN : MOUSEEVENTF_LEFTUP
        return SendInput(1, new INPUT[] { input }, Marshal.SizeOf(typeof(INPUT))) == 1;
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

    // One pass: returns how many pixels differ from the baseline by more than 'tolerance' in any channel.
    // x,y,w,h is the box holding them (all 0 when none).
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

    public static void SaveJpeg(byte[] pixels, int width, int height, int stride, string path) {
        using (Bitmap bmp = new Bitmap(width, height, PixelFormat.Format32bppArgb)) {
            BitmapData data = bmp.LockBits(new Rectangle(0, 0, width, height), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
            try {
                for (int py = 0; py < height; py++) {
                    Marshal.Copy(pixels, py * stride, IntPtr.Add(data.Scan0, py * data.Stride), width * 4);
                }
            } finally {
                bmp.UnlockBits(data);
            }
            bmp.Save(path, ImageFormat.Jpeg);
        }
    }
}
'@

Add-Type -ReferencedAssemblies System.Drawing.Common, System.Windows.Forms, System.Collections -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

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
$framesDir = Join-Path $OutDir 'frames'
New-Item -ItemType Directory -Force -Path $framesDir | Out-Null
$csvPath = Join-Path $OutDir 'frames.csv'
# CSV is built in memory and written once in finally, so an error still leaves a file behind.
$csv = [System.Text.StringBuilder]::new()
[void]$csv.Append("t_ms,foreground,x,y,w,h`n")

$proc = $null
$appExited = $true
$sessionId = (Get-Process -Id $PID).SessionId
$titles = [System.Collections.Generic.List[string]]::new()
$hidden = [System.Collections.Generic.List[string]]::new()
$backgroundStable = $false
$fpsSamples = 0
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$vs = [System.Drawing.Rectangle]::Empty
# Last box with changed pixels; the pointer plan (built at 12 s) and its marks on the t_ms clock.
$lastBox = $null
$isHover = $Scenario -like '*-hover'
$isDrag = $Scenario -eq 'floating-drag'
$plan = $null
$planStart = 0
$planNext = 0
$pressed = $false
$pointer = [System.Collections.Generic.List[object]]::new()
$marks = @{}

try {
    # 1. Plain black desktop: every other top-level window and the desktop icons hidden.
    [void][DesktopSetup]::SetSysColors(1, @(1), @(0))        # COLOR_DESKTOP -> black
    [void][DesktopSetup]::SystemParametersInfo(0x14, 0, '', 0x01 -bor 0x02)  # SPI_SETDESKWALLPAPER, clear
    foreach ($entry in [DesktopWindows]::HideTopLevel()) { $hidden.Add("window $entry") }
    foreach ($entry in [DesktopWindows]::HideDesktopIcons()) { $hidden.Add("icons $entry") }
    Start-Sleep -Milliseconds 500

    # 2. Pointer parked at the bottom centre, away from the HUD.
    $vs = [RealAppNative]::VirtualScreen()
    [void][RealAppNative]::SetCursorPos($vs.X + [int]($vs.Width / 2), $vs.Y + $vs.Height - 2)

    # 3. Wait for a still desktop: two captures 500 ms apart with zero differing pixels, up to 10 s.
    $w = 0; $h = 0; $stride = 0
    $baseline = $null
    $lastChanged = -1
    $stableWatch = [System.Diagnostics.Stopwatch]::StartNew()
    while ($stableWatch.ElapsedMilliseconds -lt 10000) {
        $firstCapture = [RealAppNative]::Capture([ref]$w, [ref]$h, [ref]$stride)
        Start-Sleep -Milliseconds 500
        $secondCapture = [RealAppNative]::Capture([ref]$w, [ref]$h, [ref]$stride)
        $bx = 0; $by = 0; $bw = 0; $bh = 0
        $lastChanged = [RealAppNative]::Diff($secondCapture, $firstCapture, $w, $h, $stride, 0, [ref]$bx, [ref]$by, [ref]$bw, [ref]$bh)
        if ($lastChanged -eq 0) { $baseline = $secondCapture; $backgroundStable = $true; break }
    }
    if (-not $backgroundStable) {
        throw "desktop did not settle within 10 s: $lastChanged pixels still change 500 ms apart"
    }
    [RealAppNative]::SaveJpeg($baseline, $w, $h, $stride, (Join-Path $framesDir 'baseline.jpg'))

    # 4. Start the app with an isolated data root, then sample until the time is up.
    $proc = Start-Process -FilePath $Exe -ArgumentList @('--user-data-dir', $DataDir) -PassThru
    $lastT = -1
    $lastJpegMs = -1000
    $lastJpegCount = 0
    $sw.Restart()
    while ($sw.ElapsedMilliseconds -lt ($Seconds * 1000)) {
        $tMs = [int]$sw.ElapsedMilliseconds
        if ($tMs -le $lastT) { Start-Sleep -Milliseconds 5; continue }   # the detector needs strictly rising t_ms
        $lastT = $tMs

        $sb = [System.Text.StringBuilder]::new(512)
        [void][RealAppNative]::GetWindowText([RealAppNative]::GetForegroundWindow(), $sb, 512)
        $title = $sb.ToString()
        if ($title -and -not $titles.Contains($title)) { $titles.Add($title) }

        $pixels = [RealAppNative]::Capture([ref]$w, [ref]$h, [ref]$stride)
        $bx = 0; $by = 0; $bw = 0; $bh = 0
        $count = [RealAppNative]::Diff($pixels, $baseline, $w, $h, $stride, 8, [ref]$bx, [ref]$by, [ref]$bw, [ref]$bh)
        $fpsSamples++
        [void]$csv.Append("$tMs,$count,$bx,$by,$bw,$bh`n")
        if ($count -gt 0) { $lastBox = @($bx, $by, $bw, $bh) }

        # Plan built once, at the first frame from 12 s, from the last non-empty box.
        # Without any box, a 2 px strip at the top centre stands in (as before).
        if ($null -eq $plan -and ($isHover -or $isDrag) -and $tMs -ge 12000) {
            $box = if ($lastBox) { $lastBox } else { @([int]($vs.Width / 2), 0, 0, 2) }
            $centreX = $vs.X + $box[0] + [int]($box[2] / 2)
            $centreY = $vs.Y + $box[1] + [int]($box[3] / 2)
            $bottomX = $vs.X + [int]($vs.Width / 2)
            $bottomY = $vs.Y + $vs.Height - 2
            $planStart = $tMs
            if ($isDrag) {
                # Drag: a 1 px start, ten steps, then release; the drag ends at (-200, +120).
                $plan = @(@{ at = 0; kind = 'move'; x = $centreX; y = $centreY })
                $plan += @{ at = 400; kind = 'press'; x = $centreX; y = $centreY; mark = 'press' }
                # The app drops the delta of the first move event (HudDragState.begin), so that event is 1 px, as a real mouse sends.
                $plan += @{ at = 450; kind = 'move'; x = $centreX - 1; y = $centreY + 1 }
                for ($i = 0; $i -lt 10; $i++) {
                    $plan += @{ at = 500 + 100 * $i; kind = 'move'; x = $centreX - 20 * ($i + 1); y = $centreY + 12 * ($i + 1) }
                }
                $plan += @{ at = 1700; kind = 'release'; x = $centreX - 200; y = $centreY + 120; mark = 'release' }
                $plan += @{ at = 2200; kind = 'move'; x = $bottomX; y = $bottomY }
            } else {
                # Hover: the hole is the last cell of the top strip (box right edge minus 60 px); the box itself otherwise.
                $aimX = $centreX
                $aimY = $centreY
                if ($Scenario -eq 'docked-top-hole-hover') {
                    $aimX = $vs.X + $box[0] + $box[2] - 60
                    $aimY = $vs.Y + $box[1] + [int]($box[3] / 2)
                }
                $plan = @(
                    @{ at = 0; kind = 'move'; x = $aimX; y = $aimY; mark = 'enter' },
                    @{ at = 3000; kind = 'move'; x = $bottomX; y = $bottomY; mark = 'leave' }
                )
            }
        }
        # At most one pointer action per iteration, each at its scheduled time (ms after the plan start).
        if ($null -ne $plan -and $planNext -lt $plan.Count -and $tMs -ge ($planStart + $plan[$planNext].at)) {
            $act = $plan[$planNext]
            $planNext++
            if ($act.kind -eq 'press') { [void][RealAppNative]::Button($true); $pressed = $true }
            elseif ($act.kind -eq 'release') { [void][RealAppNative]::Button($false); $pressed = $false }
            else { [void][RealAppNative]::MoveTo($act.x, $act.y, $vs) }
            if ($act.ContainsKey('mark')) { $marks[$act.mark] = $tMs }
            $pointer.Add([pscustomobject]@{ kind = $act.kind; x = $act.x; y = $act.y; tMs = $tMs })
        }

        # JPEG when the count moves more than 5% from the last saved JPEG, or once a second.
        $moved = [math]::Abs($count - $lastJpegCount) -gt [math]::Max(1, 0.05 * $lastJpegCount)
        if ($moved -or (($tMs - $lastJpegMs) -ge 1000)) {
            [RealAppNative]::SaveJpeg($pixels, $w, $h, $stride, (Join-Path $framesDir ('{0:D6}.jpg' -f $tMs)))
            $lastJpegMs = $tMs
            $lastJpegCount = $count
        }
        Start-Sleep -Milliseconds 50
    }
}
finally {
    # Never leave the left button down, even when the recording failed mid-drag.
    if ($pressed) {
        [void][RealAppNative]::Button($false)
        $pressed = $false
        $pointer.Add([pscustomobject]@{ kind = 'release'; x = $null; y = $null; tMs = [int]$sw.ElapsedMilliseconds })
    }
    if ($proc) {
        $appExited = $proc.HasExited
        if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    }
    [System.IO.File]::WriteAllText($csvPath, $csv.ToString(), [System.Text.UTF8Encoding]::new($false))
    # events.json: hover {enterMs, leaveMs}; drag {pressMs, releaseMs, dx, dy}. Written only when all fields exist.
    $eventsObject = $null
    if ($isHover -and $marks.ContainsKey('enter') -and $marks.ContainsKey('leave')) {
        $eventsObject = [pscustomobject]@{ enterMs = $marks['enter']; leaveMs = $marks['leave'] }
    }
    if ($isDrag -and $marks.ContainsKey('press') -and $marks.ContainsKey('release')) {
        $eventsObject = [pscustomobject]@{ pressMs = $marks['press']; releaseMs = $marks['release']; dx = -200; dy = 120 }
    }
    if ($null -ne $eventsObject) {
        $eventsJson = $eventsObject | ConvertTo-Json -Compress
        [System.IO.File]::WriteAllText((Join-Path $OutDir 'events.json'), $eventsJson, [System.Text.UTF8Encoding]::new($false))
    }

    $log = Join-Path (Join-Path $DataDir 'diagnostics') 'app.log'
    if (Test-Path -LiteralPath $log) { Copy-Item -LiteralPath $log -Destination (Join-Path $OutDir 'app.log') }

    $dpi = (Get-ItemProperty -Path 'HKCU:\Control Panel\Desktop\WindowMetrics' -ErrorAction SilentlyContinue).AppliedDPI
    [pscustomobject]@{
        scenario         = $Scenario
        screen           = [pscustomobject]@{ x = $vs.X; y = $vs.Y; width = $vs.Width; height = $vs.Height }
        dpi              = $dpi
        sessionId        = $sessionId
        backgroundStable = $backgroundStable
        hidden           = @($hidden)
        windowTitles     = @($titles)
        framesPerSecond  = [math]::Round($fpsSamples / [math]::Max(1, $sw.Elapsed.TotalSeconds), 1)
        seconds          = $Seconds
        appExited        = $appExited
        pointer          = @($pointer)
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutDir 'probe.json') -Encoding utf8
}

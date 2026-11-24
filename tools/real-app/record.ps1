#Requires -Version 7.0
<#
.SYNOPSIS
  Records the virtual screen while the real app starts: frames.csv, JPEG frames, probe.json.
.DESCRIPTION
  Runs only on GitHub Actions: it changes the desktop (black, no icons, no taskbar).
  The pointer is parked at the bottom centre, away from the HUD. No human input is simulated.
  frames.csv is t_ms,foreground,x,y,w,h. foreground = pixels that differ from the baseline (integer).
.PARAMETER Exe
  Path to the installed 'Usage Monitor.exe'.
.PARAMETER DataDir
  Passed to the app as --user-data-dir.
.PARAMETER OutDir
  Output folder (frames.csv, frames/*.jpg, app.log, probe.json).
.PARAMETER Seconds
  Recording length.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Exe,
    [Parameter(Mandatory)][string]$DataDir,
    [Parameter(Mandatory)][string]$OutDir,
    [int]$Seconds = 30
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

Add-Type -ReferencedAssemblies System.Drawing.Common, System.Windows.Forms -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public static class DesktopSetup {
    [DllImport("user32.dll", SetLastError = true)] public static extern bool SystemParametersInfo(uint uiAction, uint uiParam, string pvParam, uint fWinIni);
    [DllImport("user32.dll")] public static extern IntPtr FindWindow(string lpClassName, string lpWindowName);
    [DllImport("user32.dll")] public static extern IntPtr FindWindowEx(IntPtr parent, IntPtr after, string cls, string title);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool SetSysColors(int cElements, int[] lpaElements, int[] lpaRgbValues);
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
$fpsSamples = 0
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$vs = [System.Drawing.Rectangle]::Empty

try {
    # 1. Plain black desktop, no icons, no taskbar.
    [void][DesktopSetup]::SetSysColors(1, @(1), @(0))        # COLOR_DESKTOP -> black
    [void][DesktopSetup]::SystemParametersInfo(0x14, 0, '', 0x01 -bor 0x02)  # SPI_SETDESKWALLPAPER, clear
    $progman = [DesktopSetup]::FindWindow('Progman', $null)
    $shellView = [DesktopSetup]::FindWindowEx($progman, [IntPtr]::Zero, 'SHELLDLL_DefView', $null)
    if ($shellView -ne [IntPtr]::Zero) { [void][DesktopSetup]::ShowWindow($shellView, 0) }
    $taskbar = [DesktopSetup]::FindWindow('Shell_TrayWnd', $null)
    if ($taskbar -ne [IntPtr]::Zero) { [void][DesktopSetup]::ShowWindow($taskbar, 0) }
    Start-Sleep -Milliseconds 500

    # 2. Pointer parked at the bottom centre, away from the HUD.
    $vs = [RealAppNative]::VirtualScreen()
    [void][RealAppNative]::SetCursorPos($vs.X + [int]($vs.Width / 2), $vs.Y + $vs.Height - 2)

    # 3. Baseline: the plain desktop before the app starts.
    Start-Sleep -Milliseconds 300
    $w = 0; $h = 0; $stride = 0
    $baseline = [RealAppNative]::Capture([ref]$w, [ref]$h, [ref]$stride)
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
    if ($proc) {
        $appExited = $proc.HasExited
        if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    }
    [System.IO.File]::WriteAllText($csvPath, $csv.ToString(), [System.Text.UTF8Encoding]::new($false))

    $log = Join-Path (Join-Path $DataDir 'diagnostics') 'app.log'
    if (Test-Path -LiteralPath $log) { Copy-Item -LiteralPath $log -Destination (Join-Path $OutDir 'app.log') }

    $dpi = (Get-ItemProperty -Path 'HKCU:\Control Panel\Desktop\WindowMetrics' -ErrorAction SilentlyContinue).AppliedDPI
    [pscustomobject]@{
        screen          = [pscustomobject]@{ x = $vs.X; y = $vs.Y; width = $vs.Width; height = $vs.Height }
        dpi             = $dpi
        sessionId       = $sessionId
        windowTitles    = $titles
        framesPerSecond = [math]::Round($fpsSamples / [math]::Max(1, $sw.Elapsed.TotalSeconds), 1)
        seconds         = $Seconds
        appExited       = $appExited
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutDir 'probe.json') -Encoding utf8
}

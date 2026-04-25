Add-Type -AssemblyName System.Drawing

$pngPath = "C:\Users\edils\workspace\usage-monitor\src\desktopMain\resources\icons\app_icon.png"
$icoPath = "C:\Users\edils\workspace\usage-monitor\src\desktopMain\resources\icons\app_icon.ico"

$sizes = @(16, 32, 48, 256)

$memStream = New-Object System.IO.MemoryStream

$writer = New-Object System.IO.BinaryWriter($memStream)

$writer.Write([Int16]0)
$writer.Write([Int16]1)
$writer.Write([Int16]$sizes.Count)

$offset = 6 + (16 * $sizes.Count)

$images = @()

foreach ($size in $sizes) {
    $bmp = New-Object System.Drawing.Bitmap($pngPath)
    $resized = New-Object System.Drawing.Bitmap($bmp, $size, $size)

    $pngMemStream = New-Object System.IO.MemoryStream
    $resized.Save($pngMemStream, [System.Drawing.Imaging.ImageFormat]::Png)
    $pngBytes = $pngMemStream.ToArray()

    $widthByte = if ($size -ge 256) { 0 } else { [byte]$size }
    $heightByte = if ($size -ge 256) { 0 } else { [byte]$size }

    $writer.Write([byte]$widthByte)
    $writer.Write([byte]$heightByte)
    $writer.Write([byte]0)
    $writer.Write([byte]0)
    $writer.Write([Int16]1)
    $writer.Write([Int16]32)
    $writer.Write([Int32]$pngBytes.Length)
    $writer.Write([Int32]$offset)

    $images += ,@($pngBytes, $size)
    $offset += $pngBytes.Length

    $resized.Dispose()
    $bmp.Dispose()
    $pngMemStream.Dispose()
}

foreach ($img in $images) {
    $writer.Write($img[0])
}

$writer.Flush()
[System.IO.File]::WriteAllBytes($icoPath, $memStream.ToArray())

$writer.Dispose()
$memStream.Dispose()

Write-Host "ICO created: $icoPath"

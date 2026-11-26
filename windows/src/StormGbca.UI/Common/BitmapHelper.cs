// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using System.Runtime.InteropServices;
using Avalonia;
using Avalonia.Media.Imaging;
using Avalonia.Platform;

namespace StormGbca.UI.Common;

public static class BitmapHelper
{
    public static WriteableBitmap? CreateBgraBitmap(byte[]? bgraPixels, int width, int height)
    {
        if (bgraPixels == null || bgraPixels.Length == 0 || width <= 0 || height <= 0) return null;
        try
        {
            var wb = new WriteableBitmap(
                new PixelSize(width, height),
                new Vector(96, 96),
                PixelFormat.Bgra8888,
                AlphaFormat.Premul);
            UpdateBgraBitmap(wb, bgraPixels, width, height);
            return wb;
        }
        catch
        {
            return null;
        }
    }

    public static void UpdateBgraBitmap(WriteableBitmap wb, byte[] bgraPixels, int width, int height)
    {
        if (bgraPixels.Length == 0 || width <= 0 || height <= 0) return;
        using var locked = wb.Lock();
        int copyBytes = Math.Min(bgraPixels.Length, locked.RowBytes * height);
        Marshal.Copy(bgraPixels, 0, locked.Address, copyBytes);
    }

    public static Bitmap? LoadBitmapCached(string? path)
    {
        if (string.IsNullOrWhiteSpace(path) || !File.Exists(path)) return null;
        try
        {
            using var fs = File.OpenRead(path);
            return new Bitmap(fs);
        }
        catch
        {
            return null;
        }
    }

    public static void SaveBgraPng(byte[] bgraPixels, int width, int height, string filePath)
    {
        var wb = CreateBgraBitmap(bgraPixels, width, height);
        if (wb == null) return;
        Directory.CreateDirectory(Path.GetDirectoryName(filePath)!);
        using var fs = File.Create(filePath);
        wb.Save(fs);
    }
}

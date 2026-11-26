// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using System.Runtime.InteropServices;
using Avalonia;
using Avalonia.Media.Imaging;
using Avalonia.Platform;
using SkiaSharp;

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
        if (bgraPixels == null || bgraPixels.Length < width * height * 4 || width <= 0 || height <= 0) return;
        Directory.CreateDirectory(Path.GetDirectoryName(filePath)!);
        var info = new SKImageInfo(width, height, SKColorType.Bgra8888, SKAlphaType.Premul);
        var handle = GCHandle.Alloc(bgraPixels, GCHandleType.Pinned);
        try
        {
            using var pixmap = new SKPixmap(info, handle.AddrOfPinnedObject(), width * 4);
            using var img = SKImage.FromPixels(pixmap);
            using var data = img.Encode(SKEncodedImageFormat.Png, 95);
            using var fs = File.Create(filePath);
            data.SaveTo(fs);
        }
        finally
        {
            handle.Free();
        }
    }
}

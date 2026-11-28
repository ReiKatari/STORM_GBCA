// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using System;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Storm.AuroraFluid.Kit.Services;
using Storm.AuroraFluid.Kit.Themes;

namespace Storm.AuroraFluid.Kit.Controls;

/// <summary>
/// Canonical STORM volumetric neon checkbox.
/// Solid rounded square (CornerRadius=4) with glowing neon gradient fill when enabled,
/// and strictly ZERO checkmarks (enforcing AGENTS.md Rule 5.4).
/// </summary>
public class AuroraNeonCheckBox : Border
{
    public static readonly StyledProperty<bool> IsCheckedProperty =
        AvaloniaProperty.Register<AuroraNeonCheckBox, bool>(nameof(IsChecked), false);

    public static readonly StyledProperty<string?> LabelProperty =
        AvaloniaProperty.Register<AuroraNeonCheckBox, string?>(nameof(Label), null);

    public bool IsChecked
    {
        get => GetValue(IsCheckedProperty);
        set => SetValue(IsCheckedProperty, value);
    }

    public string? Label
    {
        get => GetValue(LabelProperty);
        set => SetValue(LabelProperty, value);
    }

    public event EventHandler<bool>? IsCheckedChanged;

    private readonly NeonBoxGlyph _boxGlyph;
    private readonly TextBlock _labelBlock;
    private readonly Action<AuroraThemeDefinition> _themeChangedHandler;
    private bool _isPointerOver;
    private bool _isPressed;

    static AuroraNeonCheckBox()
    {
        AffectsRender<AuroraNeonCheckBox>(IsCheckedProperty);
    }

    public AuroraNeonCheckBox()
    {
        Background = Brushes.Transparent;
        HorizontalAlignment = HorizontalAlignment.Stretch;
        MinHeight = 26;
        Padding = new Thickness(0, 2);
        Cursor = new Cursor(StandardCursorType.Hand);

        var row = new Grid
        {
            ColumnDefinitions = new ColumnDefinitions("Auto,*"),
            ColumnSpacing = 10,
            VerticalAlignment = VerticalAlignment.Center
        };

        _boxGlyph = new NeonBoxGlyph(this)
        {
            Width = 20,
            Height = 20,
            VerticalAlignment = VerticalAlignment.Center
        };
        row.Children.Add(_boxGlyph);

        _labelBlock = new TextBlock
        {
            FontSize = 13,
            TextWrapping = TextWrapping.Wrap,
            VerticalAlignment = VerticalAlignment.Center,
            IsVisible = false
        };
        row.Children.Add(_labelBlock);
        Grid.SetColumn(_labelBlock, 1);

        Child = row;
        _themeChangedHandler = _ => ApplyThemeVisuals();
        ApplyThemeVisuals();
    }

    protected override void OnAttachedToVisualTree(VisualTreeAttachmentEventArgs e)
    {
        base.OnAttachedToVisualTree(e);
        AuroraThemeManager.Instance.ThemeChanged -= _themeChangedHandler;
        AuroraThemeManager.Instance.ThemeChanged += _themeChangedHandler;
        ApplyThemeVisuals();
    }

    protected override void OnDetachedFromVisualTree(VisualTreeAttachmentEventArgs e)
    {
        AuroraThemeManager.Instance.ThemeChanged -= _themeChangedHandler;
        base.OnDetachedFromVisualTree(e);
    }

    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);
        if (change.Property == LabelProperty)
        {
            string? txt = Label;
            _labelBlock.Text = txt ?? string.Empty;
            _labelBlock.IsVisible = !string.IsNullOrEmpty(txt);
        }
        else if (change.Property == IsCheckedProperty)
        {
            _boxGlyph.InvalidateVisual();
        }
    }

    private void ApplyThemeVisuals()
    {
        var theme = AuroraThemeManager.Instance.CurrentTheme;
        _labelBlock.Foreground = theme.TextPrimaryBrush;
        _labelBlock.FontFamily = AuroraThemeManager.ResolveFontFamily(AuroraThemeManager.Instance.CurrentTypography);
        _boxGlyph.InvalidateVisual();
    }

    protected override void OnPointerEntered(PointerEventArgs e)
    {
        base.OnPointerEntered(e);
        _isPointerOver = true;
        AuroraTactileFeedbackService.Instance.PlayHoverTick();
        _boxGlyph.InvalidateVisual();
    }

    protected override void OnPointerExited(PointerEventArgs e)
    {
        base.OnPointerExited(e);
        _isPointerOver = false;
        _boxGlyph.InvalidateVisual();
    }

    protected override void OnPointerPressed(PointerPressedEventArgs e)
    {
        base.OnPointerPressed(e);
        _isPressed = true;
        e.Pointer.Capture(this);
        _boxGlyph.InvalidateVisual();
        e.Handled = true;
    }

    protected override void OnPointerReleased(PointerReleasedEventArgs e)
    {
        base.OnPointerReleased(e);
        if (e.Pointer.Captured == this)
        {
            e.Pointer.Capture(null);
        }
        if (_isPressed)
        {
            _isPressed = false;
            var pos = e.GetPosition(this);
            if (pos.X >= 0 && pos.Y >= 0 && pos.X <= Bounds.Width && pos.Y <= Bounds.Height)
            {
                IsChecked = !IsChecked;
                AuroraTactileFeedbackService.Instance.PlayClick();
                IsCheckedChanged?.Invoke(this, IsChecked);
            }
            _boxGlyph.InvalidateVisual();
            e.Handled = true;
        }
    }

    protected override void OnPointerCaptureLost(PointerCaptureLostEventArgs e)
    {
        base.OnPointerCaptureLost(e);
        _isPressed = false;
        _boxGlyph.InvalidateVisual();
    }

    private sealed class NeonBoxGlyph(AuroraNeonCheckBox owner) : Control
    {
        public override void Render(DrawingContext context)
        {
            var theme = AuroraThemeManager.Instance.CurrentTheme;
            var (accentPrimary, accentSec) = AuroraThemeManager.ResolveAccentColors(theme, AuroraThemeManager.Instance.CurrentAccentOverride);

            double boxSize = 18.0;
            double topOffset = (Bounds.Height - boxSize) / 2.0;
            var boxRect = new Rect(1, topOffset, boxSize, boxSize);
            float cr = 4.0f;

            if (owner.IsChecked)
            {
                var neonBrush = new LinearGradientBrush
                {
                    StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative),
                    EndPoint = new RelativePoint(1, 1, RelativeUnit.Relative),
                    GradientStops =
                    {
                        new GradientStop(accentPrimary, 0.0),
                        new GradientStop(accentSec, 1.0)
                    }
                };

                var glowBrush = new SolidColorBrush(Color.FromArgb(90, accentPrimary.R, accentPrimary.G, accentPrimary.B));
                context.DrawRectangle(glowBrush, null, new Rect(0, topOffset - 1, boxSize + 2, boxSize + 2), cr + 1, cr + 1);
                context.DrawRectangle(neonBrush, null, boxRect, cr, cr);

                var specPen = new Pen(new SolidColorBrush(Color.FromArgb(120, 255, 255, 255)), 0.8);
                context.DrawLine(specPen, new Point(boxRect.Left + 2, boxRect.Top + 1), new Point(boxRect.Right - 2, boxRect.Top + 1));
            }
            else
            {
                var bgCol = theme.CardColor;
                var uncheckBg = new SolidColorBrush(Color.FromArgb(220, bgCol.R, bgCol.G, bgCol.B));
                var borderPen = new Pen(new SolidColorBrush(owner._isPointerOver ? accentPrimary : theme.BorderColor), 1.2);
                context.DrawRectangle(uncheckBg, borderPen, boxRect, cr, cr);
            }
        }
    }
}

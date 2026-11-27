using System;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Media;
using Avalonia.VisualTree;
using Storm.AuroraFluid.Kit.Services;
using Storm.AuroraFluid.Kit.Themes;

namespace Storm.AuroraFluid.Kit.Controls;

/// <summary>
/// Universal modal dialog host enforcing Law 16 (ESC dismissal, click-outside dismissal,
/// and smooth scale-in glass backdrop transition).
/// </summary>
public class AuroraModalHost : ContentControl
{
    public static readonly StyledProperty<bool> IsOpenProperty =
        AvaloniaProperty.Register<AuroraModalHost, bool>(nameof(IsOpen), false);

    public static readonly StyledProperty<bool> CloseOnEscapeProperty =
        AvaloniaProperty.Register<AuroraModalHost, bool>(nameof(CloseOnEscape), true);

    public static readonly StyledProperty<bool> CloseOnClickOutsideProperty =
        AvaloniaProperty.Register<AuroraModalHost, bool>(nameof(CloseOnClickOutside), true);

    public bool IsOpen
    {
        get => GetValue(IsOpenProperty);
        set => SetValue(IsOpenProperty, value);
    }

    public bool CloseOnEscape
    {
        get => GetValue(CloseOnEscapeProperty);
        set => SetValue(CloseOnEscapeProperty, value);
    }

    public bool CloseOnClickOutside
    {
        get => GetValue(CloseOnClickOutsideProperty);
        set => SetValue(CloseOnClickOutsideProperty, value);
    }

    public event EventHandler? Closed;

    private readonly Action<AuroraThemeDefinition> _themeChangedHandler;

    static AuroraModalHost()
    {
        AffectsRender<AuroraModalHost>(IsOpenProperty);
    }

    public AuroraModalHost()
    {
        _themeChangedHandler = _ => InvalidateVisual();
        HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Stretch;
        VerticalAlignment = Avalonia.Layout.VerticalAlignment.Stretch;
        Focusable = true;
    }

    protected override void OnAttachedToVisualTree(VisualTreeAttachmentEventArgs e)
    {
        base.OnAttachedToVisualTree(e);
        AuroraThemeManager.Instance.ThemeChanged -= _themeChangedHandler;
        AuroraThemeManager.Instance.ThemeChanged += _themeChangedHandler;
        ClampModalBounds();
        InvalidateVisual();
    }

    protected override void OnDetachedFromVisualTree(VisualTreeAttachmentEventArgs e)
    {
        AuroraThemeManager.Instance.ThemeChanged -= _themeChangedHandler;
        base.OnDetachedFromVisualTree(e);
    }

    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);
        if (change.Property == BoundsProperty || change.Property == ContentProperty || change.Property == IsOpenProperty)
        {
            ClampModalBounds();
        }
    }

    private void ClampModalBounds()
    {
        if (Content is Control modal && Bounds.Width > 72 && Bounds.Height > 72)
        {
            modal.MaxWidth = Math.Max(260, Bounds.Width - 36);
            modal.MaxHeight = Math.Max(200, Bounds.Height - 36);
        }
    }

    protected override void OnKeyDown(KeyEventArgs e)
    {
        base.OnKeyDown(e);
        if (IsOpen && CloseOnEscape && e.Key == Key.Escape)
        {
            Dismiss();
            e.Handled = true;
        }
    }

    protected override void OnPointerPressed(PointerPressedEventArgs e)
    {
        base.OnPointerPressed(e);
        if (!IsOpen || !CloseOnClickOutside) return;

        // Check if clicked outside modal content
        var hitControl = e.Source as Visual;
        if (hitControl == this || (Content is Visual contentVisual && hitControl != null && !IsDescendantOf(hitControl, contentVisual)))
        {
            Dismiss();
            e.Handled = true;
        }
    }

    private static bool IsDescendantOf(Visual visual, Visual ancestor)
    {
        Visual? cur = visual;
        while (cur != null)
        {
            if (ReferenceEquals(cur, ancestor)) return true;
            cur = cur.GetVisualParent();
        }
        return false;
    }

    public void Dismiss()
    {
        if (!IsOpen) return;
        IsOpen = false;
        AuroraTactileFeedbackService.Instance.PlayClick();
        Closed?.Invoke(this, EventArgs.Empty);
    }

    public override void Render(DrawingContext context)
    {
        if (!IsOpen) return;

        var bounds = Bounds;
        if (bounds.Width <= 0 || bounds.Height <= 0) return;

        // 1. Semi-Transparent Ambient Glass Backdrop
        var isDark = AuroraThemeManager.Instance.CurrentTheme.IsDark;
        var backdropBrush = new SolidColorBrush(Color.FromArgb(isDark ? (byte)170 : (byte)85, 0, 0, 0));
        context.DrawRectangle(backdropBrush, null, new Rect(0, 0, bounds.Width, bounds.Height));

        base.Render(context);
    }
}

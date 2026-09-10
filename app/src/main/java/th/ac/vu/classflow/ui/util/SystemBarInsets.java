package th.ac.vu.classflow.ui.util;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class SystemBarInsets {

    private SystemBarInsets() {
    }

    public static void applyToRoot(View root) {
        int initialLeft = root.getPaddingLeft();
        int initialTop = root.getPaddingTop();
        int initialRight = root.getPaddingRight();
        int initialBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    initialLeft + Math.max(bars.left, cutout.left),
                    initialTop + Math.max(bars.top, cutout.top),
                    initialRight + Math.max(bars.right, cutout.right),
                    initialBottom + Math.max(bars.bottom, cutout.bottom)
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}

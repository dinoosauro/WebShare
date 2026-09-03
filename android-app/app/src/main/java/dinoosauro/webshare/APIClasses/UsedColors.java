package dinoosauro.webshare.APIClasses;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.Button;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;

public class UsedColors {
    String background;
    String text;
    String card;
    String secondcard;
    String secondtext;
    String accent;
    String accenttext;

    /**
     * Get the Material You color palette
     * @param button the button clicked to generate this palette. The colors of the button will be used for some variables.
     */
    public UsedColors(Button button) {
        ColorStateList tintList = button.getBackgroundTintList();
        if (tintList != null) this.accent = convertToHex(tintList.getColorForState(button.getDrawableState(), tintList.getDefaultColor()));
        this.accenttext = convertToHex(button.getCurrentTextColor());
        this.background = convertToHex(MaterialColors.getColor(button, com.google.android.material.R.attr.colorSurface));
        this.text = convertToHex(MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurface));
        this.card = convertToHex(MaterialColors.getColor(button, com.google.android.material.R.attr.colorSurfaceContainerHigh));
        this.secondcard = convertToHex(adjustLightness(MaterialColors.getColor(button, com.google.android.material.R.attr.colorSurfaceContainerHigh), MaterialColors.getColor(button, com.google.android.material.R.attr.colorSurface)));
        this.secondtext = convertToHex(MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurfaceVariant));
    }

    private String convertToHex(int source) {
        return String.format("#%06X", (0xFFFFFF & source));
    }

    public static int adjustLightness(int color, int backgroundColor) {
        float[] sourceHsl = new float[3];
        ColorUtils.colorToHSL(color, sourceHsl);
        float[] backgroundHsl = new float[3];
        ColorUtils.colorToHSL(backgroundColor, backgroundHsl);
        sourceHsl[2] += (sourceHsl[2] - backgroundHsl[2]);
        return androidx.core.graphics.ColorUtils.HSLToColor(sourceHsl);
    }

}

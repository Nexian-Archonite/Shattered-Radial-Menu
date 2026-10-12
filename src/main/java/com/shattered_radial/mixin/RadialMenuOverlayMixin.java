package com.shattered_radial.mixin;

import cc.sighs.auratip.client.render.RadialMenuOverlay;
import cc.sighs.auratip.data.RadialMenuData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(RadialMenuOverlay.class)
public abstract class RadialMenuOverlayMixin {

    @Shadow private int centerX;
    @Shadow private int centerY;
    @Shadow private float outerRadius;

    @Shadow private int hoveredIndex;
    @Shadow @Final private List<RadialMenuData.Slot> slots;

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;FIIII)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;m_280430_(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I",
                    remap = false
            ),
            remap = false
    )
    private int shatteredRadial$replaceSlotText(
            GuiGraphics graphics,
            Font font,
            Component originalText,
            int originalX,
            int originalY,
            int originalColor
    ) {
        int textColor = 0xFFD090FF;

        Component topText = Component.literal("SELECT A WEAPON");

        if (hoveredIndex >= 0 && slots != null && hoveredIndex < slots.size()) {
            RadialMenuData.Slot selected = slots.get(hoveredIndex);
            topText = selected.text().orElse(Component.literal(selected.name()));
        }

        Component bottomText = Component.literal("CLICK TO SWAP \u00B7 ALT-CLICK FOR INSTANT SWAP");

        int topY = centerY - (int) outerRadius - font.lineHeight - 10;
        int bottomY = centerY + (int) outerRadius + 10;

        graphics.drawString(
                font,
                topText,
                centerX - font.width(topText) / 2,
                topY,
                textColor
        );

        graphics.drawString(
                font,
                bottomText,
                centerX - font.width(bottomText) / 2,
                bottomY,
                textColor
        );

        return 0;
    }
}
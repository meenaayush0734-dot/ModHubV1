package com.modhub.screen;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** Adds a "ModHub" button beside the Multiplayer button on the title screen. */
public final class TitleScreenButton {
    private static final int BUTTON_W = 98;
    private static final int BUTTON_H = 20;

    private TitleScreenButton() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen)) return;

            String multiplayerLabel = Text.translatable("menu.multiplayer").getString();
            ClickableWidget multiplayer = null;
            for (ClickableWidget widget : Screens.getButtons(screen)) {
                if (widget.getMessage().getString().equals(multiplayerLabel)) {
                    multiplayer = widget;
                    break;
                }
            }

            int x;
            int y;
            if (multiplayer != null) {
                x = multiplayer.getX() + multiplayer.getWidth() + 4;
                y = multiplayer.getY();
            } else {
                // Fallback if the button is not found: right of the centred Singleplayer/Multiplayer column.
                x = scaledWidth / 2 + 104;
                y = scaledHeight / 4 + 72;
            }

            Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("ModHub"),
                            b -> client.setScreen(new ModHubScreen(screen)))
                    .dimensions(x, y, BUTTON_W, BUTTON_H)
                    .build());
        });
    }
}

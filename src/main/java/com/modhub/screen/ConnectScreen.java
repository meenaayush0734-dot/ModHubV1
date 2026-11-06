package com.modhub.screen;

import com.modhub.net.NetInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Shows the address a friend can use to join your world, with the port you enter. */
public class ConnectScreen extends Screen {
    private static final int PURPLE = 0xFF9D7BFF;
    private static final int BLUE = 0xFF4DA3FF;
    private static final int TEXT = 0xFFEEEAFF;
    private static final int MUTED = 0xFF9C95CC;
    private static final int OK = 0xFF5BE3A4;
    private static final int ERR = 0xFFFF6E8A;
    private static final int PANEL = 0xE8171538;

    private final Screen parent;
    private final String lan = NetInfo.lanAddress().orElse("Not found");
    private String publicIp = "Not checked yet";
    private String status = "Enter the port your world uses, then copy the address.";
    private int statusColor = MUTED;
    private TextFieldWidget portField;

    public ConnectScreen(Screen parent) {
        super(Text.literal("Connect"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int left = cx - 150;

        portField = addDrawableChild(new TextFieldWidget(textRenderer, left, 70, 80, 20, Text.literal("Port")));
        portField.setMaxLength(5);
        portField.setText("25565");
        portField.setTextPredicate(s -> s.matches("\\d{0,5}"));

        addDrawableChild(ButtonWidget.builder(Text.literal("Copy LAN address"), b -> copy(lanAddress()))
                .dimensions(left, 110, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Get public IP"), b -> fetchPublicIp())
                .dimensions(left + 154, 110, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Copy public address"), b -> copy(publicAddress()))
                .dimensions(left, 136, 300, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(left, height - 40, 300, 20).build());

        setInitialFocus(portField);
    }

    private String port() {
        String p = portField == null ? "" : portField.getText().trim();
        return p.isEmpty() ? "25565" : p;
    }

    private String lanAddress() {
        return lan.equals("Not found") ? "" : lan + ":" + port();
    }

    private String publicAddress() {
        return publicIp.contains(".") && !publicIp.startsWith("Not") ? publicIp + ":" + port() : "";
    }

    private void copy(String text) {
        if (text.isEmpty()) {
            setStatus("Nothing to copy yet. Get the public IP first, or check the LAN address.", ERR);
            return;
        }
        MinecraftClient.getInstance().keyboard.setClipboard(text);
        setStatus("Copied: " + text, OK);
    }

    private void fetchPublicIp() {
        setStatus("Checking your public IP...", BLUE);
        NetInfo.publicAddress().whenComplete((ip, err) -> MinecraftClient.getInstance().execute(() -> {
            if (err != null) {
                setStatus("Could not get public IP. Check your internet connection.", ERR);
            } else {
                publicIp = ip;
                setStatus("Public IP found. Port forwarding or a tunnel is still needed for internet join.", OK);
            }
        }));
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = width / 2;
        int left = cx - 150;

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Connect to your world"), cx, 14, PURPLE);
        ctx.fill(left - 8, 40, left + 308, 170, PANEL);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Port"), left, 58, MUTED);
        ctx.drawTextWithShadow(textRenderer, Text.literal("LAN (same WiFi): " + lan + ":" + port()), left, 162 - 22, TEXT);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Public IP: " + publicIp), left, 162 - 8, TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Friend on other WiFi: router must forward port " + port() + " to this PC, or use a tunnel."),
                cx, height - 60, MUTED);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status), cx, height - 76, statusColor);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

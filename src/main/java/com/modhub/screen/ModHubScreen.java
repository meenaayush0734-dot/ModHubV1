package com.modhub.screen;

import com.modhub.ModHubClient;
import com.modhub.api.ModrinthClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ModHubScreen extends Screen {
    // Dark base with blue and purple accents
    private static final int BG_TOP = 0xFF0A0D1C;
    private static final int BG_BOTTOM = 0xFF1A1040;
    private static final int PANEL = 0xD910142B;
    private static final int CARD = 0xE8171538;
    private static final int CARD_HOVER = 0xE8221D4D;
    private static final int ACCENT_BLUE = 0xFF4DA3FF;
    private static final int ACCENT_PURPLE = 0xFF9D7BFF;
    private static final int BTN_IDLE = 0xFF3A2F8C;
    private static final int BTN_BUSY = 0xFF2A2766;
    private static final int DIVIDER = 0xFF2E2960;
    private static final int TEXT = 0xFFEEEAFF;
    private static final int MUTED = 0xFF9C95CC;
    private static final int OK = 0xFF5BE3A4;
    private static final int WARN = 0xFFFFC857;
    private static final int ERR = 0xFFFF6E8A;

    private static final int SIDEBAR_W = 176;
    private static final int PAD = 16;
    private static final int SEARCH_Y = 40;
    private static final int CARDS_Y = 80;
    private static final int CARD_H = 64;
    private static final int CARD_GAP = 8;
    private static final int INSTALL_W = 84;
    private static final int INSTALL_H = 22;

    private final Screen parent;
    private final ModrinthClient api = new ModrinthClient();
    private final Set<String> installing = new HashSet<>();
    private String query = "";
    private List<ModrinthClient.Hit> results = List.of();
    private String status = "Search Modrinth for Fabric mods for Minecraft " + ModrinthClient.GAME_VERSION;
    private int statusColor = MUTED;
    private boolean loading;
    private int scroll;
    private int animTick;
    private int installedCount;
    private boolean restartNeeded;
    private TextFieldWidget searchField;

    public ModHubScreen() {
        this(null);
    }

    public ModHubScreen(Screen parent) {
        super(Text.literal("ModHub"));
        this.parent = parent;
    }

    private int mainX() { return SIDEBAR_W + PAD; }
    private int mainW() { return width - SIDEBAR_W - PAD * 2; }
    private int installX() { return mainX() + mainW() - INSTALL_W - 12; }
    private int visibleCount() { return Math.max(1, (height - 40 - CARDS_Y) / (CARD_H + CARD_GAP)); }
    private int cardY(int slot) { return CARDS_Y + slot * (CARD_H + CARD_GAP); }
    private int maxScroll() { return Math.max(0, results.size() - visibleCount()); }

    @Override
    protected void init() {
        refreshCounts();

        searchField = addDrawableChild(new TextFieldWidget(textRenderer, mainX(), SEARCH_Y, mainW() - 96, 22, Text.literal("Search")));
        searchField.setMaxLength(100);
        searchField.setText(query);

        addDrawableChild(ButtonWidget.builder(Text.literal("Search"), b -> search())
                .dimensions(mainX() + mainW() - 88, SEARCH_Y, 88, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Connect"), b -> MinecraftClient.getInstance().setScreen(new ConnectScreen(this)))
                .dimensions(PAD, height - 84, SIDEBAR_W - PAD * 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh Game"), b -> refreshGame())
                .dimensions(PAD, height - 58, SIDEBAR_W - PAD * 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(parent == null ? "Close" : "Back"), b -> close())
                .dimensions(PAD, height - 32, SIDEBAR_W - PAD * 2, 20).build());

        setInitialFocus(searchField);
        scroll = Math.min(scroll, maxScroll());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
        ctx.fill(0, 0, SIDEBAR_W, height, PANEL);
        ctx.fill(SIDEBAR_W, 0, SIDEBAR_W + 1, height, DIVIDER);

        // Sidebar stat tiles
        ctx.fill(PAD, 96, SIDEBAR_W - PAD, 136, CARD);
        ctx.fill(PAD, 144, SIDEBAR_W - PAD, 184, CARD);

        // Result cards
        int vis = visibleCount();
        for (int slot = 0; slot < vis; slot++) {
            int i = scroll + slot;
            if (i >= results.size()) break;
            int y = cardY(slot);
            boolean hover = mouseX >= mainX() && mouseX < mainX() + mainW() && mouseY >= y && mouseY < y + CARD_H;
            ctx.fill(mainX(), y, mainX() + mainW(), y + CARD_H, hover ? CARD_HOVER : CARD);
            ctx.fillGradient(mainX(), y, mainX() + 3, y + CARD_H, ACCENT_BLUE, ACCENT_PURPLE);

            ModrinthClient.Hit hit = results.get(i);
            int bx = installX();
            int by = y + (CARD_H - INSTALL_H) / 2;
            boolean onButton = mouseX >= bx && mouseX < bx + INSTALL_W && mouseY >= by && mouseY < by + INSTALL_H;
            int btn = installing.contains(hit.id()) ? BTN_BUSY : (onButton ? ACCENT_BLUE : BTN_IDLE);
            ctx.fill(bx, by, bx + INSTALL_W, by + INSTALL_H, btn);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        animTick++;

        // Sidebar header and stats
        ctx.drawTextWithShadow(textRenderer, Text.literal("ModHub"), PAD, 16, ACCENT_PURPLE);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Minecraft " + ModrinthClient.GAME_VERSION + " · Fabric"), PAD, 30, MUTED);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Installed jars"), PAD + 8, 102, MUTED);
        ctx.drawTextWithShadow(textRenderer, Text.literal(String.valueOf(installedCount)), PAD + 8, 116, TEXT);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Restart needed"), PAD + 8, 150, MUTED);
        ctx.drawTextWithShadow(textRenderer, Text.literal(restartNeeded ? "Yes" : "No"), PAD + 8, 164, restartNeeded ? WARN : OK);

        // Main header
        ctx.drawTextWithShadow(textRenderer, Text.literal("Browse Mods"), mainX(), 16, TEXT);
        if (!results.isEmpty()) {
            String count = results.size() + " results";
            ctx.drawTextWithShadow(textRenderer, Text.literal(count),
                    mainX() + mainW() - textRenderer.getWidth(count), 16, MUTED);
        }

        // Search hint
        if (searchField != null && searchField.getText().isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("Search mods, e.g. sodium, iris, lithium"),
                    mainX() + 6, SEARCH_Y + 7, MUTED);
        }

        // Cards text and labels
        int textW = installX() - mainX() - 24;
        int vis = visibleCount();
        for (int slot = 0; slot < vis; slot++) {
            int i = scroll + slot;
            if (i >= results.size()) break;
            ModrinthClient.Hit h = results.get(i);
            int y = cardY(slot);
            ctx.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(h.title(), textW)),
                    mainX() + 12, y + 8, TEXT);
            String meta = "by " + h.author() + "  ·  " + String.format("%,d", h.downloads()) + " downloads";
            ctx.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(meta, textW)),
                    mainX() + 12, y + 20, ACCENT_BLUE);
            ctx.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(h.description(), textW)),
                    mainX() + 12, y + 34, MUTED);

            Text label = Text.literal(installing.contains(h.id()) ? "Installing..." : "Install");
            int lx = installX() + (INSTALL_W - textRenderer.getWidth(label)) / 2;
            ctx.drawTextWithShadow(textRenderer, label, lx, y + (CARD_H - INSTALL_H) / 2 + 7, TEXT);
        }

        // Empty state
        if (results.isEmpty() && !loading) {
            int cx = mainX() + mainW() / 2;
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("No results yet"), cx, CARDS_Y + 40, TEXT);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Type a mod name above and press Enter"), cx, CARDS_Y + 56, MUTED);
        }

        // Status line with animated loading dots
        String dots = loading ? ".".repeat((animTick / 10) % 4) : "";
        ctx.drawTextWithShadow(textRenderer, Text.literal(status + dots), mainX(), height - 30, statusColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        int vis = visibleCount();
        for (int slot = 0; slot < vis; slot++) {
            int i = scroll + slot;
            if (i >= results.size()) break;
            int by = cardY(slot) + (CARD_H - INSTALL_H) / 2;
            if (mouseX >= installX() && mouseX < installX() + INSTALL_W && mouseY >= by && mouseY < by + INSTALL_H) {
                install(results.get(i));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int next = scroll - (int) Math.signum(verticalAmount);
        scroll = Math.max(0, Math.min(next, maxScroll()));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchField != null && searchField.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            search();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void search() {
        query = searchField.getText().trim();
        if (query.isEmpty()) {
            setStatus("Type a mod name to search.", WARN);
            return;
        }
        loading = true;
        setStatus("Searching Modrinth", MUTED);
        String q = query;
        CompletableFuture.supplyAsync(() -> {
            try {
                return api.search(q);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((list, err) -> MinecraftClient.getInstance().execute(() -> {
            loading = false;
            if (err != null) {
                setStatus("Search failed: " + rootMessage(err), ERR);
                return;
            }
            results = list;
            scroll = 0;
            if (list.isEmpty()) {
                setStatus("No Fabric mods found for \"" + q + "\"", WARN);
            } else {
                setStatus(list.size() + " result(s) for Minecraft " + ModrinthClient.GAME_VERSION, OK);
            }
        }));
    }

    private void install(ModrinthClient.Hit hit) {
        if (!installing.add(hit.id())) return;
        setStatus("Installing " + hit.title() + "...", ACCENT_BLUE);
        CompletableFuture.supplyAsync(() -> {
            try {
                return api.download(hit, ModHubClient.modsDir());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((path, err) -> MinecraftClient.getInstance().execute(() -> {
            installing.remove(hit.id());
            if (err != null) {
                setStatus("Install failed: " + rootMessage(err), ERR);
            } else {
                refreshCounts();
                setStatus("Installed " + path.getFileName() + ". Restart Minecraft to load it.", OK);
            }
        }));
    }

    private void refreshGame() {
        MinecraftClient mc = MinecraftClient.getInstance();
        setStatus("Refreshing...", ACCENT_BLUE);
        // Supported reload path: reloads resource packs and client assets.
        mc.reloadResources().thenRun(() -> mc.execute(() -> {
            refreshCounts();
            if (restartNeeded) {
                setStatus("Restart Required: the mods folder changed. Java mods only load at startup.", WARN);
            } else {
                setStatus("Refreshed. Resource packs reloaded.", OK);
            }
        }));
    }

    private void refreshCounts() {
        installedCount = ModHubClient.listJars().size();
        restartNeeded = ModHubClient.restartRequired();
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) cur = cur.getCause();
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }
}

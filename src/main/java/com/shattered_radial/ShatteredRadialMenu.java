
package com.shattered_radial;

import cc.sighs.auratip.api.action.Actions;
import cc.sighs.auratip.api.client.RadialMenuClientApi;
import cc.sighs.auratip.api.radiamenu.RadialMenuRegistry;
import cc.sighs.auratip.api.radiamenu.icon.ItemIcon;
import cc.sighs.auratip.data.RadialMenuData;
import cc.sighs.auratip.data.action.Action;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Mod(ShatteredRadialMenu.MODID)
public class ShatteredRadialMenu {
    public static final String MODID = "shattered_radial";

    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL =
            NetworkRegistry.newSimpleChannel(
                    new ResourceLocation(MODID, "main"),
                    () -> PROTOCOL_VERSION,
                    PROTOCOL_VERSION::equals,
                    PROTOCOL_VERSION::equals
            );

    public static final KeyMapping RADIAL_KEY = new KeyMapping(
            "key.shattered_radial.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.categories.inventory"
    );

    private static final Set<String> SBW_GUN_PATH_LIST = Set.of(
            "taser", "glock_17", "glock_18", "mp_443", "m_1911",
            "homemade_shotgun", "trachelium", "mp_5", "vector",
            "ak_47", "ak_12", "sks", "m_4", "hk_416", "qbz_95",
            "qbz_191", "insidious", "mk_14", "ql_1031", "marlin",
            "k_98", "mosin_nagant", "svd", "awm", "m_98b",
            "sentinel", "hunting_rifle", "ntw_20", "m_870", "aa_12",
            "devotion", "rpk", "m_60", "m_2_hb", "minigun", "m_79",
            "secondary_cataclysm", "rpg", "javelin", "igla_9k38",
            "bocek", "super_star_shooter"
    );

    public ShatteredRadialMenu() {
        var modBus =
                FMLJavaModLoadingContext.get().getModEventBus();

        modBus.addListener(this::onRegisterKeyMappings);

        CHANNEL.registerMessage(
                0,
                SwapGunPacket.class,
                SwapGunPacket::encode,
                SwapGunPacket::decode,
                SwapGunPacket::handle
        );

        registerActionHandlers();
    }

    private void onRegisterKeyMappings(
            RegisterKeyMappingsEvent event
    ) {
        event.register(RADIAL_KEY);
    }

    public static class SwapGunPacket {
        private final int slotIndex;
        private final boolean isAltHeld;

        public SwapGunPacket(int slotIndex, boolean isAltHeld) {
            this.slotIndex = slotIndex;
            this.isAltHeld = isAltHeld;
        }

        public static void encode(
                SwapGunPacket packet,
                FriendlyByteBuf buffer
        ) {
            buffer.writeVarInt(packet.slotIndex);
            buffer.writeBoolean(packet.isAltHeld);
        }

        public static SwapGunPacket decode(
                FriendlyByteBuf buffer
        ) {
            return new SwapGunPacket(
                    buffer.readVarInt(),
                    buffer.readBoolean()
            );
        }

        public static void handle(
                SwapGunPacket packet,
                Supplier<NetworkEvent.Context> contextSupplier
        ) {
            NetworkEvent.Context context = contextSupplier.get();

            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();

                if (player == null) {
                    return;
                }

                var inventory = player.getInventory();
                int targetSlot = packet.slotIndex;
                int selectedSlot = inventory.selected;

                // Only permit actual player inventory slots.
                if (targetSlot < 0
                        || targetSlot >= inventory.getContainerSize()) {
                    return;
                }

                if (targetSlot == selectedSlot) {
                    return;
                }

                ItemStack heldItem =
                        inventory.getItem(selectedSlot).copy();
                ItemStack targetItem =
                        inventory.getItem(targetSlot).copy();

                inventory.setItem(targetSlot, heldItem);
                inventory.setItem(selectedSlot, targetItem);

                player.inventoryMenu.broadcastChanges();

                if (player.containerMenu != player.inventoryMenu) {
                    player.containerMenu.broadcastChanges();
                }
            });

            context.setPacketHandled(true);
        }
    }

    private void registerActionHandlers() {
        ResourceLocation actionType =
                new ResourceLocation(MODID, "handle_gun_click");

        Actions.register(actionType, params -> {
            int slotIndex = params.getInt("slot_index", -1);

            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null || mc.getWindow() == null) {
                return;
            }

            Player player = mc.player;

            if (slotIndex < 0
                    || slotIndex >= player.getInventory().getContainerSize()) {
                return;
            }

            long windowHandle = mc.getWindow().getWindow();

            boolean altHeld =
                    InputConstants.isKeyDown(
                            windowHandle,
                            GLFW.GLFW_KEY_LEFT_ALT
                    )
                            || InputConstants.isKeyDown(
                            windowHandle,
                            GLFW.GLFW_KEY_RIGHT_ALT
                    );

            // Predict the swap locally for immediate Alt-click feedback.
            if (altHeld) {
                int selectedSlot = player.getInventory().selected;

                if (slotIndex != selectedSlot) {
                    ItemStack heldItem =
                            player.getInventory().getItem(selectedSlot).copy();
                    ItemStack targetItem =
                            player.getInventory().getItem(slotIndex).copy();

                    player.getInventory().setItem(slotIndex, heldItem);
                    player.getInventory().setItem(selectedSlot, targetItem);
                }
            }

            // Always send the swap to the server for authoritative syncing.
            CHANNEL.sendToServer(
                    new SwapGunPacket(slotIndex, altHeld)
            );
        });
    }

    @Mod.EventBusSubscriber(
            modid = MODID,
            value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.FORGE
    )
    public static class ClientGameBusEvents {

        @SubscribeEvent
        public static void onClientTick(
                TickEvent.ClientTickEvent event
        ) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null || mc.level == null) {
                return;
            }

            while (RADIAL_KEY.consumeClick()) {
                openShatteredRadialMenu(mc.player);
            }
        }
    }

    private static void openShatteredRadialMenu(Player player) {
        List<GunEntry> gunEntries = getPlayerGunEntries(player);

        if (gunEntries.isEmpty()) {
            return;
        }

        List<RadialMenuData.Slot> slots = new ArrayList<>();
        String slotBorderColor = "#66D090FF";

        for (GunEntry entry : gunEntries) {
            Action clickAction = Actions.script(
                    new ResourceLocation(
                            MODID,
                            "handle_gun_click"
                    ),
                    Map.of("slot_index", entry.slotIndex)
            );

            String gunName =
                    entry.stack.getHoverName().getString();

            Component displayComponent =
                    Component.literal(gunName);

            slots.add(new RadialMenuData.Slot(
                    gunName,
                    new ItemIcon(entry.stack),
                    clickAction,
                    Optional.of(displayComponent),
                    Optional.of(slotBorderColor)
            ));
        }

        List<String> purpleRingColors = List.of(
                "#66702080",
                "#66A040D0"
        );

        RadialMenuData.MenuSettings settings =
                new RadialMenuData.MenuSettings(
                        45,
                        105,
                        1.2f,
                        Optional.empty(),
                        Optional.of("#66D090FF"),
                        Optional.of(purpleRingColors)
                );

        ResourceLocation menuId =
                new ResourceLocation(
                        MODID,
                        "shattered_gun_menu"
                );

        RadialMenuData menuData =
                new RadialMenuData(menuId, settings, slots);

        RadialMenuRegistry.setMenus(
                MODID,
                List.of(menuData)
        );

        RadialMenuClientApi.open(menuId);
    }

    private static class GunEntry {
        final int slotIndex;
        final ItemStack stack;

        GunEntry(int slotIndex, ItemStack stack) {
            this.slotIndex = slotIndex;
            this.stack = stack;
        }
    }

    private static List<GunEntry> getPlayerGunEntries(
            Player player
    ) {
        List<GunEntry> entries = new ArrayList<>();

        for (int i = 0;
             i < player.getInventory().getContainerSize();
             i++) {

            ItemStack stack = player.getInventory().getItem(i);

            if (stack.isEmpty()) {
                continue;
            }

            ResourceLocation itemId =
                    stack.getItem()
                            .builtInRegistryHolder()
                            .key()
                            .location();

            String namespace = itemId.getNamespace().toLowerCase();
            String path = itemId.getPath().toLowerCase();
            String className =
                    stack.getItem().getClass().getName().toLowerCase();

            boolean isTaCZ = namespace.equals("tacz");

            boolean isSBW =
                    namespace.equals("superbwarfare")
                            || namespace.equals("sbw");

            if (isTaCZ) {
                boolean isTaCZExcluded =
                        path.contains("bullet")
                                || path.contains("ammo")
                                || path.contains("mag")
                                || path.contains("attachment")
                                || path.contains("perk");

                if (!isTaCZExcluded
                        && (className.contains("gun")
                        || path.contains("gun"))) {
                    entries.add(new GunEntry(i, stack));
                }
            } else if (isSBW) {
                if (SBW_GUN_PATH_LIST.contains(path)) {
                    entries.add(new GunEntry(i, stack));
                }
            } else {
                boolean isGenericGun =
                        className.contains("gunitem")
                                || className.contains("firearmitem")
                                || className.contains("weaponitem");

                if (isGenericGun) {
                    entries.add(new GunEntry(i, stack));
                }
            }
        }

        return entries;
    }
}

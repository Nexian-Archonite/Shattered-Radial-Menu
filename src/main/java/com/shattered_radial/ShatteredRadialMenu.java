package com.shattered_radial;

import cc.sighs.auratip.api.action.Actions;
import cc.sighs.auratip.api.client.RadialMenuClientApi;
import cc.sighs.auratip.api.radiamenu.RadialMenuRegistry;
import cc.sighs.auratip.api.radiamenu.icon.ItemIcon;
import cc.sighs.auratip.data.RadialMenuData;
import cc.sighs.auratip.data.action.Action;
import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Mod(ShatteredRadialMenu.MODID)
public class ShatteredRadialMenu {
    public static final String MODID = "shattered_radial";

    public static final KeyMapping RADIAL_KEY = new KeyMapping(
            "key.shattered_radial.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.categories.inventory"
    );

    // Explicit set of all 42 Superb Warfare firearm registry IDs
    private static final Set<String> SBW_GUN_PATH_LIST = Set.of(
            "taser", "glock_17", "glock_18", "mp_443", "m_1911", "homemade_shotgun",
            "trachelium", "mp_5", "vector", "ak_47", "ak_12", "sks", "m_4", "hk_416",
            "qbz_95", "qbz_191", "insidious", "mk_14", "ql_1031", "marlin", "k_98",
            "mosin_nagant", "svd", "awm", "m_98b", "sentinel", "hunting_rifle", "ntw_20",
            "m_870", "aa_12", "devotion", "rpk", "m_60", "m_2_hb", "minigun", "m_79",
            "secondary_cataclysm", "rpg", "javelin", "igla_9k38", "bocek", "super_star_shooter"
    );

    public ShatteredRadialMenu(IEventBus modBus) {
        modBus.addListener(this::onRegisterKeyMappings);
        modBus.addListener(this::registerPayloads);
        registerActionHandlers();
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(RADIAL_KEY);
    }

    public record SwapGunPayload(int slotIndex, boolean isAltHeld) implements CustomPacketPayload {
        public static final Type<SwapGunPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "swap_gun"));
        public static final StreamCodec<ByteBuf, SwapGunPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SwapGunPayload::slotIndex,
                ByteBufCodecs.BOOL, SwapGunPayload::isAltHeld,
                SwapGunPayload::new
        );

        @Override
        public Type<SwapGunPayload> type() {
            return TYPE;
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MODID).versioned("1.0");
        registrar.playToServer(
                SwapGunPayload.TYPE,
                SwapGunPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        int selectedSlot = player.getInventory().selected;
                        ItemStack heldItem = player.getMainHandItem().copy();
                        ItemStack slotItem = player.getInventory().getItem(payload.slotIndex()).copy();

                        player.getInventory().setItem(payload.slotIndex(), heldItem);
                        player.getInventory().setItem(selectedSlot, slotItem);
                        player.containerMenu.broadcastChanges();
                    }
                })
        );
    }

    private void registerActionHandlers() {
        ResourceLocation actionType = ResourceLocation.fromNamespaceAndPath(MODID, "handle_gun_click");

        Actions.register(actionType, params -> {
            int slotIndex = params.getInt("slot_index", -1);
            if (slotIndex == -1) return;

            long windowHandle = Minecraft.getInstance().getWindow().getWindow();
            boolean altHeld = InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_LEFT_ALT)
                    || InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_RIGHT_ALT);

            if (altHeld) {
                // Instant client-side swap execution for Alt-clicks
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    Player player = mc.player;
                    int selectedHotbarSlot = player.getInventory().selected;
                    ItemStack heldItem = player.getMainHandItem().copy();
                    ItemStack targetItem = player.getInventory().getItem(slotIndex).copy();

                    player.getInventory().setItem(slotIndex, heldItem);
                    player.getInventory().setItem(selectedHotbarSlot, targetItem);
                }
            } else {
                // Standard server-authoritative swap packet for normal clicks
                PacketDistributor.sendToServer(new SwapGunPayload(slotIndex, false));
            }
        });
    }

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientGameBusEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            while (RADIAL_KEY.consumeClick()) {
                openShatteredRadialMenu(mc.player);
            }
        }
    }

    private static void openShatteredRadialMenu(Player player) {
        List<GunEntry> gunEntries = getPlayerGunEntries(player);
        if (gunEntries.isEmpty()) return;

        List<RadialMenuData.Slot> slots = new ArrayList<>();
        String slotBorderColor = "#66D090FF";

        for (GunEntry entry : gunEntries) {
            Action clickAction = Actions.script(
                    ResourceLocation.fromNamespaceAndPath(MODID, "handle_gun_click"),
                    Map.of("slot_index", entry.slotIndex)
            );

            String gunName = entry.stack.getHoverName().getString();
            Component displayComponent = Component.literal(gunName);

            slots.add(new RadialMenuData.Slot(
                    gunName,
                    new ItemIcon(entry.stack),
                    clickAction,
                    Optional.of(displayComponent),
                    Optional.of(slotBorderColor)
            ));
        }



        List<String> purpleRingColors = List.of(
                "#66702080", // Deep purple, 40% opacity
                "#66A040D0"  // Vivid purple, 40% opacity
        );

        RadialMenuData.MenuSettings settings = new RadialMenuData.MenuSettings(
                45,
                105,
                1.2f,
                Optional.empty(),
                Optional.of("#66D090FF"),
                Optional.of(purpleRingColors)
        );

        ResourceLocation menuId = ResourceLocation.fromNamespaceAndPath(MODID, "shattered_gun_menu");
        RadialMenuData menuData = new RadialMenuData(menuId, settings, slots);

        RadialMenuRegistry.setMenus(MODID, List.of(menuData));
        RadialMenuClientApi.open(menuId);
    }

    private static class GunEntry {
        final int slotIndex;
        final ItemStack stack;
        public GunEntry(int slotIndex, ItemStack stack) {
            this.slotIndex = slotIndex;
            this.stack = stack;
        }
    }

    private static List<GunEntry> getPlayerGunEntries(Player player) {
        List<GunEntry> entries = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                ResourceLocation itemId = stack.getItem().builtInRegistryHolder().key().location();
                String namespace = itemId.getNamespace().toLowerCase();
                String path = itemId.getPath().toLowerCase();
                String className = stack.getItem().getClass().getName().toLowerCase();

                boolean isTaCZ = namespace.equals("tacz");
                boolean isSBW = namespace.equals("superbwarfare") || namespace.equals("sbw");

                if (isTaCZ) {
                    boolean isTaCZExcluded = path.contains("bullet") || path.contains("ammo") || path.contains("mag") || path.contains("attachment") || path.contains("perk");
                    if (!isTaCZExcluded && (className.contains("gun") || path.contains("gun"))) {
                        entries.add(new GunEntry(i, stack));
                    }
                } else if (isSBW) {
                    if (SBW_GUN_PATH_LIST.contains(path)) {
                        entries.add(new GunEntry(i, stack));
                    }
                } else {
                    boolean isGenericGun = className.contains("gunitem") || className.contains("firearmitem") || className.contains("weaponitem");
                    if (isGenericGun) {
                        entries.add(new GunEntry(i, stack));
                    }
                }
            }
        }
        return entries;
    }
}
package com.macepower.addon.modules;

import com.macepower.addon.MacePowerAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;

import java.util.Comparator;
import java.util.stream.StreamSupport;

public class MacePower extends Module {
    public enum WeaponType {
        Mace(Items.MACE),
        Sword(Items.DIAMOND_SWORD),
        Axe(Items.DIAMOND_AXE),
        Trident(Items.TRIDENT),
        All(null),
        Any(null);

        private final Item item;

        WeaponType(Item item) {
            this.item = item;
        }

        public Item getItem() {
            return item;
        }

        public boolean matches(ItemStack stack) {
            if (this == Any) return true;
            if (this == All) {
                Item i = stack.getItem();
                return isSword(i) || i instanceof AxeItem || i == Items.MACE || i instanceof TridentItem;
            }
            return stack.getItem() == item;
        }

        private boolean isSword(Item item) {
            return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD
                || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD
                || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD;
        }
    }

    public enum ServerType {
        Paper,
        Spigot
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgHits = settings.createGroup("Hits");
    private final SettingGroup sgMisc = settings.createGroup("Misc");

    private final Setting<Double> macePower = sgGeneral.add(new DoubleSetting.Builder()
        .name("mace-power")
        .description("The power multiplier for the mace attack.")
        .defaultValue(1.0)
        .range(0.1, 10.0)
        .sliderRange(0.1, 5.0)
        .build()
    );

    private final Setting<Double> belowHit1 = sgHits.add(new DoubleSetting.Builder()
        .name("below-hit-1")
        .description("First hit when target is below this Y level.")
        .defaultValue(0.0)
        .range(-64.0, 320.0)
        .sliderRange(-64.0, 64.0)
        .build()
    );

    private final Setting<Double> hit2 = sgHits.add(new DoubleSetting.Builder()
        .name("hit-2")
        .description("Second hit configuration.")
        .defaultValue(3.0)
        .range(0.0, 20.0)
        .sliderRange(0.0, 10.0)
        .build()
    );

    private final Setting<Double> extraHit = sgHits.add(new DoubleSetting.Builder()
        .name("extra-hit")
        .description("Extra hit damage multiplier.")
        .defaultValue(1.5)
        .range(0.0, 20.0)
        .sliderRange(0.0, 10.0)
        .build()
    );

    private final Setting<Integer> noGroundPackets = sgGeneral.add(new IntSetting.Builder()
        .name("no-ground-packets")
        .description("Number of no-ground packets to send.")
        .defaultValue(3)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> disableWhenBlocked = sgMisc.add(new BoolSetting.Builder()
        .name("disable-when-blocked")
        .description("Disable the module when you are blocked/hit.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> hitAmount = sgHits.add(new IntSetting.Builder()
        .name("hit-amount")
        .description("Number of hits to perform.")
        .defaultValue(1)
        .range(1, 10)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Boolean> silentSwitch = sgMisc.add(new BoolSetting.Builder()
        .name("silent-switch")
        .description("Silently switch to the weapon without client-side animation.")
        .defaultValue(true)
        .build()
    );

    private final Setting<WeaponType> weapon = sgGeneral.add(new EnumSetting.Builder<WeaponType>()
        .name("weapon")
        .description("The weapon type to use.")
        .defaultValue(WeaponType.Mace)
        .build()
    );

    private final Setting<ServerType> serverType = sgMisc.add(new EnumSetting.Builder<ServerType>()
        .name("server-type")
        .description("The server type you are playing on.")
        .defaultValue(ServerType.Paper)
        .build()
    );

    private final Setting<Double> extraHitAmount = sgHits.add(new DoubleSetting.Builder()
        .name("extra-hit-amount")
        .description("Additional extra hit amount.")
        .defaultValue(1.0)
        .range(0.0, 20.0)
        .sliderRange(0.0, 10.0)
        .build()
    );

    private int hitCount;
    private int packetCounter;
    private boolean wasBlocked;
    private Entity target;

    public MacePower() {
        super(MacePowerAddon.CATEGORY, "mace-power", "Advanced mace combat module with customizable hit configurations.");
    }

    @Override
    public void onActivate() {
        hitCount = 0;
        packetCounter = 0;
        wasBlocked = false;
        target = null;
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (disableWhenBlocked.get() && mc.player.hurtTime > 0) {
            wasBlocked = true;
            toggle();
            return;
        }

        target = findTarget();
        if (target == null) return;

        if (silentSwitch.get()) {
            FindItemResult result = findWeapon();
            if (result.found()) {
                InvUtils.swap(result.slot(), true);
            } else {
                return;
            }
        } else {
            if (!holdingValidWeapon()) return;
        }

        if (shouldAttack()) {
            attack(target);
        }
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (event.entity == target) {
            hitCount++;
            sendNoGroundPackets();

            if (hitCount >= hitAmount.get()) {
                hitCount = 0;
                target = null;
            }
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (serverType.get() == ServerType.Spigot && packetCounter > 0) {
            if (event.packet instanceof PlayerMoveC2SPacket packet) {
                if (packet.isOnGround()) {
                    packetCounter--;
                }
            }
        }
    }

    private boolean shouldAttack() {
        if (target == null) return false;
        if (hitCount >= hitAmount.get()) return false;
        return mc.player.getAttackCooldownProgress(0.5f) >= 1.0f;
    }

    private void attack(Entity entity) {
        if (mc.player == null) return;

        mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.attackEntity(mc.player, entity);

        Rotations.rotate(Rotations.getYaw(entity), Rotations.getPitch(entity));
    }

    private void sendNoGroundPackets() {
        if (mc.player == null || noGroundPackets.get() <= 0) return;

        if (serverType.get() == ServerType.Paper) {
            for (int i = 0; i < noGroundPackets.get(); i++) {
                mc.player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(),
                        mc.player.getY() + 0.01,
                        mc.player.getZ(),
                        false,
                        false
                    )
                );
            }
            mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    true,
                    false
                )
            );
        } else {
            packetCounter = noGroundPackets.get();
        }
    }

    private Entity findTarget() {
        if (mc.player == null || mc.world == null) return null;

        return StreamSupport.stream(mc.world.getEntities().spliterator(), false)
            .filter(entity -> entity != mc.player)
            .filter(entity -> entity instanceof LivingEntity)
            .filter(entity -> !entity.isRemoved())
            .filter(entity -> {
                if (entity instanceof PlayerEntity player) {
                    return player != mc.player && !player.isCreative() && !player.isSpectator();
                }
                return true;
            })
            .filter(entity -> mc.player.distanceTo(entity) <= 4.5)
            .min(Comparator.comparingDouble(entity -> mc.player.distanceTo(entity)))
            .orElse(null);
    }

    private FindItemResult findWeapon() {
        return InvUtils.findInHotbar(itemStack -> weapon.get().matches(itemStack));
    }

    private boolean holdingValidWeapon() {
        if (mc.player == null) return false;
        ItemStack held = mc.player.getMainHandStack();
        return weapon.get().matches(held);
    }

    @Override
    public String getInfoString() {
        return weapon.get().name();
    }
}

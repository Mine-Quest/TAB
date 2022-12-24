package me.neznamy.tab.platforms.velocity;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.protocol.packet.*;
import com.velocitypowered.proxy.protocol.packet.scoreboard.ScoreboardDisplay;
import com.velocitypowered.proxy.protocol.packet.scoreboard.ScoreboardObjective;
import com.velocitypowered.proxy.protocol.packet.scoreboard.ScoreboardScore;
import com.velocitypowered.proxy.protocol.packet.scoreboard.Team;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.protocol.*;
import me.neznamy.tab.api.protocol.PacketPlayOutChat.ChatMessageType;
import me.neznamy.tab.api.util.Preconditions;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.proxy.ProxyTabPlayer;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.LogManager;

/**
 * TabPlayer implementation for Velocity
 */
public class VelocityTabPlayer extends ProxyTabPlayer {

    /**
     * Map of methods executing tasks using Velocity API calls or sending the actual packet
     */
    private final Map<Class<?>, Consumer<Object>> packetMethods
            = new HashMap<Class<?>, Consumer<Object>>(){{
        put(BossBar.class, packet -> sendNativePacket(packet));
        put(PacketPlayOutChat.class, packet -> handle((PacketPlayOutChat) packet));
        put(RemovePlayerInfo.class, packet -> sendNativePacket(packet));
        put(UpsertPlayerInfo.class, packet -> sendNativePacket(packet));
        put(LegacyPlayerListItem.class, packet -> sendNativePacket(packet));
        put(Team.class, packet -> sendNativePacket(packet));
        put(ScoreboardScore.class, packet -> sendNativePacket(packet));
        put(ScoreboardObjective.class, packet -> sendNativePacket(packet));
        put(ScoreboardDisplay.class, packet -> sendNativePacket(packet));
        put(HeaderAndFooter.class, packet -> sendNativePacket(packet));
    }};

    /**
     * Constructs new instance for given player
     *
     * @param   p
     *          velocity player
     */
    public VelocityTabPlayer(Player p) {
        super(p, p.getUniqueId(), p.getUsername(), p.getCurrentServer().isPresent() ?
                p.getCurrentServer().get().getServerInfo().getName() : "-", p.getProtocolVersion().getProtocol(),
                TabAPI.getInstance().getConfig().getBoolean("use-online-uuid-in-tablist", true));
    }
    
    @Override
    public boolean hasPermission0(String permission) {
        return getPlayer().hasPermission(permission);
    }
    
    @Override
    public int getPing() {
        return (int) getPlayer().getPing();
    }
    
    @Override
    public void sendPacket(Object packet) {
        if (packet == null || !getPlayer().isActive()) return;
        packetMethods.get(packet.getClass()).accept(packet);
    }

    /**
     * Sends a raw packet to the client over velocity
     *
     * @param packet the packet to send
     */
    private void sendNativePacket(Object packet) {
        TAB.getInstance().debug(String.format("Sending %s across the wire.", packet.getClass().getSimpleName()));

        this.getPlayer().sendRawPacket(packet);
    }

    /**
     * Handles PacketPlayOutChat request using Velocity API
     *
     * @param   packet
     *          Packet request to handle
     */
    private void handle(PacketPlayOutChat packet) {
        Component message = Main.getInstance().convertComponent(packet.getMessage(), getVersion());
        if (packet.getType() == ChatMessageType.GAME_INFO) {
            getPlayer().sendActionBar(message);
        } else {
            getPlayer().sendMessage(message);
        }
    }

    /**
     * Returns TabList entry with specified UUID. If no such entry was found,
     * a new, dummy entry is returned to avoid NPE.
     *
     * @param   id
     *          UUID to get entry by
     * @return  TabList entry with specified UUID
     */
    private TabListEntry getEntry(UUID id) {
        for (TabListEntry entry : getPlayer().getTabList().getEntries()) {
            if (entry.getProfile().getId().equals(id)) return entry;
        }
        //return dummy entry to not cause NPE
        //possibly add logging into the future to see when this happens
        return TabListEntry.builder()
                .tabList(getPlayer().getTabList())
                .displayName(Component.text(""))
                .gameMode(0)
                .profile(new GameProfile(UUID.randomUUID(), "empty", new ArrayList<>()))
                .latency(0)
                .build();
    }
    
    @Override
    public Skin getSkin() {
        if (getPlayer().getGameProfile().getProperties().size() == 0) return null; //offline mode
        return new Skin(getPlayer().getGameProfile().getProperties().get(0).getValue(), getPlayer().getGameProfile().getProperties().get(0).getSignature());
    }
    
    @Override
    public Player getPlayer() {
        return (Player) player;
    }
    
    @Override
    public boolean isOnline() {
        return getPlayer().isActive();
    }

    @Override
    public int getGamemode() {
        return getEntry(getTablistUUID()).getGameMode();
    }

    @Override
    public Object getProfilePublicKey() {
        return getPlayer().getIdentifiedKey();
    }

    @Override
    public UUID getChatSessionId() {
        return null; // not supported on velocity
    }

    @Override
    public void sendPluginMessage(byte[] message) {
        Preconditions.checkNotNull(message, "message");
        try {
            Optional<ServerConnection> server = getPlayer().getCurrentServer();
            if (server.isPresent()) {
                server.get().sendPluginMessage(Main.getInstance().getMinecraftChannelIdentifier(), message);
                TAB.getInstance().getThreadManager().packetSent("Plugin Message (" + new String(message) + ")");
            }
        } catch (IllegalStateException e) {
            //java.lang.IllegalStateException: Not connected to server!
        }
    }
}
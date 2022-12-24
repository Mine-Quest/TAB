package me.neznamy.tab.platforms.velocity;

import com.google.common.collect.Lists;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.protocol.packet.*;
import com.velocitypowered.proxy.protocol.packet.chat.RemoteChatSession;
import com.velocitypowered.proxy.protocol.packet.scoreboard.ScoreboardDisplay;
import com.velocitypowered.proxy.protocol.packet.scoreboard.ScoreboardObjective;
import com.velocitypowered.proxy.protocol.packet.scoreboard.ScoreboardScore;
import com.velocitypowered.proxy.protocol.packet.scoreboard.Team;
import me.neznamy.tab.api.ProtocolVersion;
import me.neznamy.tab.api.chat.EnumChatFormat;
import me.neznamy.tab.api.chat.IChatBaseComponent;
import me.neznamy.tab.api.protocol.*;
import me.neznamy.tab.shared.TAB;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements the {@link PacketBuilder} class for the velocity platform.
 */
public class VelocityPacketBuilder extends PacketBuilder {

    @Override
    public Object build(PacketPlayOutBoss packet, ProtocolVersion clientVersion) throws ReflectiveOperationException {
        if (clientVersion.getMinorVersion() < 9) return null;
        BossBar bungeePacket = new BossBar();
        bungeePacket.setUuid(packet.getId());
        bungeePacket.setAction(packet.getAction().ordinal());
        bungeePacket.setPercent(packet.getPct());
        bungeePacket.setName(packet.getName() == null ? null : IChatBaseComponent.optimizedComponent(packet.getName()).toString(clientVersion));
        bungeePacket.setColor(packet.getColor() == null ? 0 : packet.getColor().ordinal());
        bungeePacket.setOverlay(packet.getOverlay() == null ? 0: packet.getOverlay().ordinal());
        bungeePacket.setFlags(packet.getFlags());
        return bungeePacket;
    }

    @Override
    public Object build(PacketPlayOutChat packet, ProtocolVersion clientVersion) {
        //Let the VelocityPlayer handle the chat packet naturally, no need to do extra on it.
        return packet;
    }

    @Override
    public Object build(PacketPlayOutPlayerInfo packet, ProtocolVersion clientVersion) {
        if (clientVersion.getNetworkId() >= ProtocolVersion.V1_19_3.getNetworkId()) {
            if (packet.getActions().contains(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER)) {
                RemovePlayerInfo remove = new RemovePlayerInfo();
                remove.setProfilesToRemove(packet.getEntries().stream().map(PacketPlayOutPlayerInfo.PlayerInfoData::getUniqueId).collect(Collectors.toList()));
                return remove;
            }
            List<UpsertPlayerInfo.Entry> items = new ArrayList<>();
            for (PacketPlayOutPlayerInfo.PlayerInfoData data : packet.getEntries()) {
                UpsertPlayerInfo.Entry item = new UpsertPlayerInfo.Entry(data.getUniqueId());
                if (data.getDisplayName() != null) item.setDisplayName(GsonComponentSerializer.gson().deserialize(data.getDisplayName().toString(clientVersion)));
                if (data.getGameMode() != null) item.setGameMode(data.getGameMode().ordinal()-1);
                item.setListed(data.isListed());
                item.setLatency(data.getLatency());
                GameProfile gameProfile;
                if (data.getSkin() != null) {
                    gameProfile = new GameProfile(data.getUniqueId(), data.getName(), Lists.newArrayList(new GameProfile.Property("textures", data.getSkin().getValue(), data.getSkin().getSignature())));
                } else {
                    gameProfile = new GameProfile(data.getUniqueId(), data.getName(), Lists.newArrayList());
                }
                item.setProfile(gameProfile);
                item.setChatSession(new RemoteChatSession(data.getChatSessionId(), (IdentifiedKey) data.getProfilePublicKey()));
                items.add(item);
            }
            UpsertPlayerInfo bungeePacket = new UpsertPlayerInfo();
            UpsertPlayerInfo.Action[] array = packet.getActions().stream().map(action -> UpsertPlayerInfo.Action.valueOf(
                    action.toString().replace("GAME_MODE", "GAMEMODE"))).toArray(UpsertPlayerInfo.Action[]::new);
            bungeePacket.addAllActions(Arrays.asList(array));
            bungeePacket.addAllEntries(items);
            return bungeePacket;
        }
        List<LegacyPlayerListItem.Item> items = new ArrayList<>();
        for (PacketPlayOutPlayerInfo.PlayerInfoData data : packet.getEntries()) {
            LegacyPlayerListItem.Item item = new LegacyPlayerListItem.Item(data.getUniqueId());
            if (data.getDisplayName() != null) {
                item.setDisplayName(GsonComponentSerializer.gson().deserialize(data.getDisplayName().toString(clientVersion)));
            }
            if (data.getGameMode() != null) item.setGameMode(data.getGameMode().ordinal()-1);
            item.setLatency(data.getLatency());
            if (data.getSkin() != null) {
                item.setProperties(Lists.newArrayList(new GameProfile.Property("textures", data.getSkin().getValue(), data.getSkin().getSignature())));
            } else {
                item.setProperties(Lists.newArrayList());
            }
            item.setName(data.getName());
            item.setPlayerKey((IdentifiedKey) data.getProfilePublicKey());
            items.add(item);
        }
        PacketPlayOutPlayerInfo.EnumPlayerInfoAction action = packet.getActions().contains(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER) ?
                PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER : packet.getActions().iterator().next();
        //Velocity doesn't have the INITIALIZE_CHAT option, so this is necessary
        int actionNum;
        switch (action) {
            case ADD_PLAYER:
                actionNum = 0;
                break;
            case UPDATE_GAME_MODE:
                actionNum = 1;
                break;
            case UPDATE_LATENCY:
                actionNum = 2;
                break;
            case UPDATE_DISPLAY_NAME:
                actionNum = 3;
                break;
            case REMOVE_PLAYER:
                actionNum = 4;
                break;
            default:
                TAB.getInstance().getErrorManager().printError(String.format("Can't send action %s to legacy player!", action.name()));
                return null;
        }
        return new LegacyPlayerListItem(actionNum, items);
    }

    @Override
    public Object build(PacketPlayOutScoreboardTeam packet, ProtocolVersion clientVersion) throws ReflectiveOperationException {
        int color = 0;
        if (clientVersion.getMinorVersion() >= 13) {
            color = (packet.getColor() != null ? packet.getColor() : EnumChatFormat.lastColorsOf(packet.getPlayerPrefix())).ordinal();
        }
        Team teamPacket = new Team();
        teamPacket.setColor(color);
        teamPacket.setName(packet.getName());
        teamPacket.setMode((byte) packet.getAction());
        teamPacket.setDisplayName(jsonOrCut(packet.getName(), clientVersion, 16));
        teamPacket.setPrefix(jsonOrCut(packet.getPlayerPrefix(), clientVersion, 16));
        teamPacket.setSuffix(jsonOrCut(packet.getPlayerSuffix(), clientVersion, 16));
        teamPacket.setNameTagVisibility(packet.getNameTagVisibility());
        teamPacket.setCollisionRule(packet.getCollisionRule());
        teamPacket.setFriendlyFire((byte) packet.getOptions());
        teamPacket.setPlayers(packet.getPlayers().toArray(new String[0]));
        return teamPacket;
    }

    @Override
    public Object build(PacketPlayOutScoreboardScore packet, ProtocolVersion clientVersion) throws ReflectiveOperationException {
        ScoreboardScore score = new ScoreboardScore();
        score.setAction((byte) packet.getAction().ordinal());
        score.setItemName(packet.getPlayer());
        score.setScoreName(packet.getObjectiveName());
        score.setValue(packet.getScore());
        return score;
    }

    @Override
    public Object build(PacketPlayOutScoreboardObjective packet, ProtocolVersion clientVersion) throws ReflectiveOperationException {
        ScoreboardObjective objective = new ScoreboardObjective();
        objective.setName(packet.getObjectiveName());
        objective.setValue(jsonOrCut(packet.getDisplayName(), clientVersion, 32));
        objective.setType(packet.getRenderType() == null ? null : ScoreboardObjective.HealthDisplay.valueOf(packet.getRenderType().toString()));
        objective.setAction((byte) packet.getAction());
        return objective;
    }

    @Override
    public Object build(PacketPlayOutPlayerListHeaderFooter packet, ProtocolVersion clientVersion) throws ReflectiveOperationException {
        return new HeaderAndFooter(packet.getHeader().toString(clientVersion, true), packet.getFooter().toString(clientVersion, true));
    }

    @Override
    public Object build(PacketPlayOutScoreboardDisplayObjective packet, ProtocolVersion clientVersion) throws ReflectiveOperationException {
        ScoreboardDisplay display = new ScoreboardDisplay();
        display.setPosition((byte) packet.getSlot());
        display.setName(packet.getObjectiveName());
        return display;
    }
}

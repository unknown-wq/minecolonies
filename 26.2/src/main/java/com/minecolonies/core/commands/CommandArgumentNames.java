package com.minecolonies.core.commands;

public abstract class CommandArgumentNames
{
    public static final String PLAYERNAME_ARG    = "playername";
    public static final String COLONYID_ARG      = "colonyID";
    public static final String CITIZENID_ARG     = "citizenID";
    public static final String RAID_TYPE_ARG     = "raidtype";
    public static final String RAID_TIME_ARG     = "raidtime";
    public static final String RAID_AMOUNT_ARG   = "raidamount";
    public static final String RAID_LOCATION_ARG = "raidlocation";
    public static final String RAID_STRENGTH_ARG = "raidstrength";
    public static final String RAID_SIZE         = "size";
    public static final String RAID_STRENGTH     = "strength";
    public static final String RAID_TERRITORY    = "territory";
    public static final String RAID_LOCATE       = "where";
    public static final String RAID_TP           = "tp";
    public static final String RAID_STOP         = "stop";
    public static final String SHIP_ARG          = "allowships";
    public static final String RAID_NOW          = "now";
    public static final String RAID_TONIGHT      = "tonight";
    public static final String POS_ARG           = "location";

    /**
     * The anti-air command's two verbs, spelled the same as the raid command's on purpose: a player who
     * has learned that "where reports and tp takes you there" should not have to learn it twice.
     */
    public static final String ANTIAIR_LOCATE = "where";
    public static final String ANTIAIR_TP     = "tp";

    /**
     * The anti-air command's tuning verbs, and the name of the one optional value every one of them takes.
     * <p>
     * Each of the four is a subcommand that <em>reports</em> when given no value and <em>sets</em> when given
     * one, which is the shape {@code /mc colony blastprotection} established and is also why an omitted number
     * can never be read as zero: there is no code path from "no argument" to a setter at all.
     */
    public static final String ANTIAIR_SETTINGS = "settings";
    public static final String ANTIAIR_RANGE    = "range";
    public static final String ANTIAIR_RATE     = "rate";
    public static final String ANTIAIR_DAMAGE   = "damage";
    public static final String ANTIAIR_MINLEVEL = "minlevel";
    public static final String ANTIAIR_RESET    = "reset";
    public static final String ANTIAIR_VALUE    = "value";

    /**
     * The aircraft command's verbs, spelled the same again and for the same reason.
     */
    public static final String AIRCRAFT_LOCATE = "where";
    public static final String AIRCRAFT_TP     = "tp";

    public static final String RANGE_ARG    = "range";
    public static final String ADD_ARG      = "add";
    public static final String CHILDREN_ARG = "children";
}

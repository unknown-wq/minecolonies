package com.minecolonies.api.colony.guardtype.registry;

import com.minecolonies.api.colony.guardtype.GuardType;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.resources.Identifier;
import java.util.function.Supplier;


public final class ModGuardTypes
{

    public static final Identifier KNIGHT_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "knight");
    public static final Identifier RANGER_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "ranger");
    public static final Identifier DRUID_ID  = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "druid");
    public static final Identifier CAVALRY_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cavalry");
    public static final Identifier HUSCARL_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "huscarl");
    public static final Identifier MARKSMAN_ID  = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "marksman");

    public static Supplier<GuardType> knight;
    public static Supplier<GuardType> ranger;
    public static Supplier<GuardType> druid;
    public static Supplier<GuardType> huscarl;
    public static Supplier<GuardType> marksman;
    public static Supplier<GuardType> cavalry;

    private ModGuardTypes()
    {
        throw new IllegalStateException("Tried to initialize: ModGuardTypes but this is a Utility class.");
    }
}

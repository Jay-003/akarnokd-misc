package hu.akarnokd.fallout76;

import java.util.HashMap;
import java.util.Map;

public class FunctionMapping {

    static final Map<Integer, String> FUNCTION_MAP = new HashMap<>();
    static {
        FUNCTION_MAP.put(4778, "WornHasKeyword");
        FUNCTION_MAP.put(4173, "GetRandomPercent");
        FUNCTION_MAP.put(4170, "GetGlobalValue");
        FUNCTION_MAP.put(4639, "GetQuestCompleted");
        FUNCTION_MAP.put(4675, "EditorLocationHasKeyword");


        FUNCTION_MAP.put(4544, "HasPerk");
        FUNCTION_MAP.put(4165, "GetIsRace");
        FUNCTION_MAP.put(4176, "GetLevel");
        FUNCTION_MAP.put(4154, "GetStage");
        FUNCTION_MAP.put(4659, "LocationHasRefType");


        FUNCTION_MAP.put(4110, "GetActorValue");
        FUNCTION_MAP.put(4143, "GetItemCount");
        FUNCTION_MAP.put(4656, "HasKeyword");
        FUNCTION_MAP.put(4101, "GetLocked");
        FUNCTION_MAP.put(4161, "GetLockLevel");


        FUNCTION_MAP.put(4168, "GetIsID");
        FUNCTION_MAP.put(4455, "GetInCurrentLoc");
        FUNCTION_MAP.put(4657, "HasRefType");
        FUNCTION_MAP.put(4097, "GetDistance");
        FUNCTION_MAP.put(4278, "GetEquipped");


        FUNCTION_MAP.put(4661, "GetIsEditorLocation");
        FUNCTION_MAP.put(4266, "GetDayOfWeek");
        FUNCTION_MAP.put(4955, "HasEntitlement");
        FUNCTION_MAP.put(4949, "HasLearnedRecipe");
        FUNCTION_MAP.put(4971, "IsTrueForConditionForm");


        FUNCTION_MAP.put(4945, "GetIsInRegion");
        FUNCTION_MAP.put(4953, "GetNumTimesCompletedQuest");
        FUNCTION_MAP.put(4933, "IsActivePlayer");
        FUNCTION_MAP.put(4994, "GetWorldType");
        FUNCTION_MAP.put(4942, "GetActorValueForCurrentLocation");


        FUNCTION_MAP.put(4929, "GetStageDoneUniqueQuest");
        FUNCTION_MAP.put(4950, "HasActiveMagicEffect");
        FUNCTION_MAP.put(4884, "LocationHasPlayerOwnedWorkshop");
        FUNCTION_MAP.put(4396, "IsInInterior");
        FUNCTION_MAP.put(9100, "PlayerHasQuest");
    }

}

import com.blackjack200.ouranos.network.mapping.ItemTranslator;
import com.blackjack200.ouranos.network.mapping.ItemTypeDictionary;
import com.blackjack200.ouranos.network.mapping.LegacyItemIdToStringIdMap;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.junit.Test;

import java.util.Objects;

@Log4j2
public class MappingTest {
    @Test
    public void testItemIdMapping() {
        val mapping = LegacyItemIdToStringIdMap.getInstance();
        val protocolIdA = 527;
        val protocolIdB = 545;
        val id = "minecraft:iron_ingot";
        val resultA = mapping.fromString(protocolIdA, id);
        log.info("result A={}", resultA);
        log.info("result A2={}", mapping.fromNumeric(protocolIdA, resultA));

        val resultB = mapping.fromString(protocolIdB, id);
        log.info("result B={}", resultB);
        log.info("result B2={}", mapping.fromNumeric(protocolIdB, resultB));
    }

    @Test
    public void testItemTranslator() {
        val translator = ItemTranslator.getInstance();
        val id = "minecraft:iron_ingot";

        val protocolIdA = 527;
        val protocolIdB = 545;

        val coreIdA = ItemTypeDictionary.getInstance().fromStringId(protocolIdA, id);
        int[] resultA = translator.toNetworkId(protocolIdA, coreIdA, 0);
        assert Objects.requireNonNull(translator.fromNetworkId(protocolIdA, resultA[0], resultA[1]))[0] == coreIdA;

        val coreIdB = ItemTypeDictionary.getInstance().fromStringId(protocolIdB, id);
        int[] resultB = translator.toNetworkId(protocolIdB, coreIdB, 0);
        assert translator.fromNetworkIdNotNull(protocolIdA, resultA[0], resultA[1]) == translator.fromNetworkIdNotNull(protocolIdB, resultB[0], resultB[1]);
    }
}

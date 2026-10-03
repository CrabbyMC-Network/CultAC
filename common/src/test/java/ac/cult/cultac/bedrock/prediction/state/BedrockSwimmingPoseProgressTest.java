package ac.cult.cultac.bedrock.prediction.state;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BedrockSwimmingPoseProgressTest {
    @Test
    public void decaysToZeroWithBedrockFloatStepSemantics() {
        double swimAmount = 1.0D;

        for (int tick = 0; tick < 10; tick++) {
            swimAmount = BedrockSwimmingPoseProgress.nextSwimAmount(swimAmount, false);
        }

        assertEquals(0.0D, swimAmount, 0.0D);
    }
}

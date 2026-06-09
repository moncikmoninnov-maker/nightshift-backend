package `fun`.nightshift.launcher.client.crypto

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Unit tests for mod classification logic.
 * 
 * These tests verify:
 * - Premium mod identification (NightShift Client Beta prefix)
 * - Public mod identification (all other filenames)
 * - Case-sensitive matching
 * - Edge cases (empty strings, special characters)
 */
class ModClassificationTest : StringSpec({
    
    "classifyMod should identify premium mod with exact prefix" {
        val fileName = "NightShift Client Beta-2.3.jar"
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PREMIUM
    }
    
    "classifyMod should identify premium mod with different version" {
        val fileName = "NightShift Client Beta-1.0.0.jar"
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PREMIUM
    }
    
    "classifyMod should identify premium mod without extension" {
        val fileName = "NightShift Client Beta-2.3"
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PREMIUM
    }
    
    "classifyMod should identify public mod (Fabric API)" {
        val fileName = "fabric-api-0.119.4-1.21.4.jar"
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PUBLIC
    }
    
    "classifyMod should identify public mod (Sodium)" {
        val fileName = "sodium-fabric-0.5.8+mc1.21.jar"
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PUBLIC
    }
    
    "classifyMod should identify public mod (Lithium)" {
        val fileName = "lithium-fabric-mc1.21-0.12.1.jar"
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PUBLIC
    }
    
    "classifyMod should identify public mod (Baritone)" {
        val fileName = "baritone-api-fabric-1.13.1.jar"
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PUBLIC
    }
    
    "classifyMod should be case-sensitive (lowercase prefix)" {
        val fileName = "nightshift client beta-2.3.jar"
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PUBLIC
    }
    
    "classifyMod should be case-sensitive (uppercase prefix)" {
        val fileName = "NIGHTSHIFT CLIENT BETA-2.3.jar"
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PUBLIC
    }
    
    "classifyMod should handle empty string as public" {
        val fileName = ""
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PUBLIC
    }
    
    "classifyMod should handle partial prefix match as public" {
        val fileName = "NightShift Client-2.3.jar"
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PUBLIC
    }
    
    "classifyMod should handle prefix in middle of filename as public" {
        val fileName = "some-NightShift Client Beta-2.3.jar"
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PUBLIC
    }
    
    "classifyMod should handle special characters in premium mod name" {
        val fileName = "NightShift Client Beta-2.3-special_build.jar"
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PREMIUM
    }
    
    "classifyMod should handle whitespace after prefix" {
        val fileName = "NightShift Client Beta- 2.3.jar"
        
        val classification = classifyMod(fileName)
        
        classification shouldBe ModClassification.PREMIUM
    }
})

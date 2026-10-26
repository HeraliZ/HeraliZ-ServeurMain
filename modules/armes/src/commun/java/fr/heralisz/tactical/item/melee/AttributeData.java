package fr.heralisz.tactical.item.melee;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public class AttributeData {
   private final List<AttributeData.AttributeInfo> attributes = new ArrayList<>();

   public List<AttributeData.AttributeInfo> getAttributes() {
      return this.attributes;
   }

   public record AttributeInfo(
      @SerializedName("id") ResourceLocation id, @SerializedName("amount") float amount, @SerializedName("operation") Operation operation
   ) {
   }

   public static class Deserializer implements JsonDeserializer<AttributeData> {
      public AttributeData deserialize(JsonElement element, Type type, JsonDeserializationContext ctx) throws JsonParseException {
         if (!element.isJsonObject()) {
            throw new JsonParseException("Expected a JsonObject, get " + element);
         }

         JsonObject jsonObject = (JsonObject)element;
         AttributeData attributeData = new AttributeData();

         for (Entry<String, JsonElement> entry : jsonObject.asMap().entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id != null) {
               if (entry.getValue().isJsonPrimitive()) {
                  JsonPrimitive primitive = entry.getValue().getAsJsonPrimitive();
                  if (primitive.isNumber()) {
                     float amount = primitive.getAsFloat();
                     attributeData.attributes.add(new AttributeData.AttributeInfo(id, amount, Operation.ADDITION));
                  }
               } else if (entry.getValue().isJsonObject()) {
                  JsonObject attributeObject = entry.getValue().getAsJsonObject();
                  float amount = GsonHelper.getAsFloat(attributeObject, "amount", 0.0F);
                  String operationId = GsonHelper.getAsString(attributeObject, "operation", "addition");

                  Operation operation = switch (operationId) {
                     case "addition" -> Operation.ADDITION;
                     case "percent" -> Operation.MULTIPLY_BASE;
                     case "multiply" -> Operation.MULTIPLY_TOTAL;
                     default -> Operation.ADDITION;
                  };
                  attributeData.attributes.add(new AttributeData.AttributeInfo(id, amount, operation));
               }
            }
         }

         return attributeData;
      }
   }
}

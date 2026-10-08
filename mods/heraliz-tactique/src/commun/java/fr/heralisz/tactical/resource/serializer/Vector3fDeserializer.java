package fr.heralisz.tactical.resource.serializer;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import org.joml.Vector3f;

public class Vector3fDeserializer implements JsonDeserializer<Vector3f> {
   public Vector3f deserialize(JsonElement ele, Type type, JsonDeserializationContext ctx) throws JsonParseException {
      if (!ele.isJsonArray()) {
         throw new JsonParseException("Expected an array for Vector3f, but found: " + ele);
      } else {
         JsonArray array = ele.getAsJsonArray();
         if (array.size() != 3) {
            throw new JsonParseException("Expected an array of 3 elements for Vector3f, but found: " + ele);
         } else {
            return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
         }
      }
   }
}

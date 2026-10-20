package com.finfast.common.money.json;

import com.finfast.common.money.VndAmount;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;

/** HTTP/JSON adapter, deliberately separate from the Java-only money primitive. */
@JacksonComponent
public class VndAmountJson {

    public static class Serializer extends ValueSerializer<VndAmount> {
        @Override
        public void serialize(VndAmount amount, JsonGenerator generator, SerializationContext context) {
            generator.writeString(amount.toDecimalString());
        }
    }

    public static class Deserializer extends ValueDeserializer<VndAmount> {
        @Override
        public VndAmount deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(VndAmount.class, "VND must be a JSON decimal string");
            }
            try {
                return VndAmount.parse(parser.getString());
            } catch (IllegalArgumentException exception) {
                return context.reportInputMismatch(VndAmount.class, "VND decimal string is invalid or out of range");
            }
        }
    }
}

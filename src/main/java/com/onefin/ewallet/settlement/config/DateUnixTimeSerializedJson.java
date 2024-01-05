package com.onefin.ewallet.settlement.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;

@Component
public class DateUnixTimeSerializedJson extends JsonSerializer<Date> {


	@Override
	public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {

		gen.writeString(String.valueOf(value.getTime()));

	}
}

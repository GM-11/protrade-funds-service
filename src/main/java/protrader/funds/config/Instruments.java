package protrader.funds.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Symbol id → ticker ("1" → "MOOG"), read once from the shared config/instruments.json (the gateway reads the same
 * file). Display only: an unknown id is shown as its number, like the gateway does; a missing file is a warning.
 */
public final class Instruments {

	private static final Logger log = LoggerFactory.getLogger(Instruments.class);
	private static final JsonMapper JSON = JsonMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	record InstrumentJson(@JsonProperty("id") int id, @JsonProperty("ticker") String ticker) {
	}

	record FileJson(@JsonProperty("instruments") List<InstrumentJson> instruments) {
	}

	private final Map<Integer, String> tickers;

	public Instruments(Map<Integer, String> tickers) {
		this.tickers = Map.copyOf(tickers);
	}

	public static Instruments load(String file) {
		try {
			FileJson parsed = JSON.readValue(Files.readAllBytes(Path.of(file)), FileJson.class);
			Map<Integer, String> map = new HashMap<>();
			if (parsed.instruments() != null) {
				for (InstrumentJson i : parsed.instruments()) {
					map.put(i.id(), i.ticker());
				}
			}
			log.info("instruments: {} symbols from {}", map.size(), file);
			return new Instruments(map);
		} catch (Exception e) {
			log.warn("instruments: could not read {} ({}); symbols will be shown as numbers", file, e.toString());
			return new Instruments(Map.of());
		}
	}

	public String ticker(int symbol) {
		String t = tickers.get(symbol);
		return t != null ? t : Integer.toString(symbol);
	}
}

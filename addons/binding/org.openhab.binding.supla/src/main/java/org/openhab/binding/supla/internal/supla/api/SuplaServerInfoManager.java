package org.openhab.binding.supla.internal.supla.api;

import com.google.gson.reflect.TypeToken;
import org.openhab.binding.supla.internal.api.ServerInfoManager;
import org.openhab.binding.supla.internal.mappers.JsonMapper;
import org.openhab.binding.supla.internal.server.http.HttpExecutor;
import org.openhab.binding.supla.internal.server.http.Request;
import org.openhab.binding.supla.internal.server.http.Response;
import org.openhab.binding.supla.internal.supla.entities.SuplaServerInfo;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

public final class SuplaServerInfoManager implements ServerInfoManager {
    private static final Type MAP_TYPE = new TypeToken<Map<String, SuplaServerInfo>>() {
    }.getType();
    private static final String KEY_FOR_SERVER_INFO = "data";
    private final HttpExecutor httpExecutor;
    private final JsonMapper jsonMapper;

    public SuplaServerInfoManager(HttpExecutor httpExecutor, JsonMapper jsonMapper) {
        this.httpExecutor = checkNotNull(httpExecutor);
        this.jsonMapper = checkNotNull(jsonMapper);
    }

    @Override
    public Optional<SuplaServerInfo> obtainServerInfo() {
        final Response response = httpExecutor.get(new Request("/server-info"));
        final Map<String, SuplaServerInfo> map = jsonMapper.to(MAP_TYPE, response.getResponse());
        if (map.containsKey(KEY_FOR_SERVER_INFO)) {
            return Optional.of(map.get(KEY_FOR_SERVER_INFO));
        } else {
            return Optional.empty();
        }
    }
}
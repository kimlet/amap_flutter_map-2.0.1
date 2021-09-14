package com.amap.flutter.map.overlays.marker;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.amap.api.maps.AMap;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Poi;
import com.amap.flutter.amap_flutter_map.R;
import com.amap.flutter.map.MyMethodCallHandler;
import com.amap.flutter.map.overlays.AbstractOverlayController;
import com.amap.flutter.map.utils.Const;
import com.amap.flutter.map.utils.ConvertUtil;
import com.amap.flutter.map.utils.LogUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

/**
 * @author whm
 * @date 2020/11/6 5:38 PM
 * @mail hongming.whm@alibaba-inc.com
 * @since
 */
public class MarkersController
        extends AbstractOverlayController<MarkerController>
        implements MyMethodCallHandler,
        AMap.OnMapClickListener,
        AMap.OnMarkerClickListener,
        AMap.OnMarkerDragListener,
        AMap.OnPOIClickListener {
    private static final String CLASS_NAME = "MarkersController";
    private String selectedMarkerDartId;
    private Context context;

    public MarkersController(Context context, MethodChannel methodChannel, AMap amap) {
        super(methodChannel, amap);
        this.context = context;
        amap.addOnMarkerClickListener(this);
        amap.addOnMarkerDragListener(this);
        amap.addOnMapClickListener(this);
        amap.addOnPOIClickListener(this);
    }

    @Override
    public String[] getRegisterMethodIdArray() {
        return Const.METHOD_ID_LIST_FOR_MARKER;
    }


    @Override
    public void doMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        LogUtil.i(CLASS_NAME, "doMethodCall===>" + call.method);
        switch (call.method) {
            case Const.METHOD_MARKER_UPDATE:
                invokeMarkerOptions(call, result);
                break;
        }
    }


    /**
     * 执行主动方法更新marker
     *
     * @param methodCall
     * @param result
     */
    public void invokeMarkerOptions(MethodCall methodCall, MethodChannel.Result result) {
        if (null == methodCall) {
            return;
        }
        Object markersToAdd = methodCall.argument("markersToAdd");
        addByList((List<Object>) markersToAdd);
        Object markersToChange = methodCall.argument("markersToChange");
        updateByList((List<Object>) markersToChange);
        Object markerIdsToRemove = methodCall.argument("markerIdsToRemove");
        removeByIdList((List<Object>) markerIdsToRemove);
        result.success(null);
    }

    public void addByList(List<Object> markersToAdd) {
        if (markersToAdd != null) {
            for (Object markerToAdd : markersToAdd) {
                add(markerToAdd);
            }
        }
    }

    private void add(Object markerObj) {
        if (null != amap) {
            MarkerOptionsBuilder builder = new MarkerOptionsBuilder();
            String dartMarkerId = MarkerUtil.interpretMarkerOptions(markerObj, builder);
            final Map<?, ?> data = ConvertUtil.toMap(markerObj);
            final Object infoWindow = data.get("infoWindow");
            String title = (String) ((Map<String, Object>) infoWindow).get("title");
            String snippet = (String) ((Map<String, Object>) infoWindow).get("snippet");

            if (!TextUtils.isEmpty(dartMarkerId)) {
                MarkerOptions markerOptions = builder.build();
                markerOptions.infoWindowEnable(false);
                markerOptions.icon(getBitmapDescriptor(false, title, snippet));
                final Marker marker = amap.addMarker(markerOptions);
                Object clickable = ConvertUtil.getKeyValueFromMapObject(markerObj, "clickable");
                if (null != clickable) {
                    marker.setClickable(ConvertUtil.toBoolean(clickable));
                }
                MarkerController markerController = new MarkerController(marker);
                controllerMapByDartId.put(dartMarkerId, markerController);
                idMapByOverlyId.put(marker.getId(), dartMarkerId);
                showMarkerInfoWindow(dartMarkerId);
            }
        }

    }

    protected BitmapDescriptor getBitmapDescriptor(boolean selected, String title, String snippet) {
        if ("index".equals(snippet)) {
            return BitmapDescriptorFactory.fromResource(R.drawable.ic_my_position);
        }
        View view = null;
        view = View.inflate(context, R.layout.info_window, null);
        TextView textView = ((TextView) view.findViewById(R.id.tv_title));
        TextView subTitleView = ((TextView) view.findViewById(R.id.tv_sub_title));

        if (TextUtils.isEmpty(snippet)) {
            subTitleView.setVisibility(View.GONE);
        } else {
            subTitleView.setVisibility(View.VISIBLE);
        }
        if (null != snippet && snippet.startsWith("{")) {
            try {
                JSONObject jsonObject = new JSONObject(snippet);
                String type = jsonObject.getString("type");
                String num = jsonObject.getString("num");
                boolean defaultSelected = jsonObject.getBoolean("selected");
                if ("DISTRICT".equals(type) || "PRECINCT".equals(type)) {
                    view.setBackgroundResource(selected || defaultSelected ? R.drawable.ic_map_circle_selected : R.drawable.ic_map_circle);
                    textView.setTextColor(selected || defaultSelected ? context.getResources().getColor(android.R.color.white) : context.getResources().getColor(android.R.color.black));
                    subTitleView.setTextColor(selected || defaultSelected ? context.getResources().getColor(android.R.color.white) : context.getResources().getColor(android.R.color.black));
                    subTitleView.setText(num);
                } else if ("COMMUNITY".equals(type)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        view.setBackground(selected ? context.getDrawable(R.drawable.ic_info_window_selected_bg) : context.getDrawable(R.drawable.ic_info_window_bg));
                    }
//                    textView.setTextColor(selected ? context.getResources().getColor(android.R.color.holo_green_dark) : context.getResources().getColor(android.R.color.white));
                    subTitleView.setVisibility(View.GONE);
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        textView.setText(title);


        return BitmapDescriptorFactory.fromView(view);
    }

    private void updateByList(List<Object> markersToChange) {
        if (markersToChange != null) {
            for (Object markerToChange : markersToChange) {
                update(markerToChange);
            }
        }
    }

    private void update(Object markerToChange) {
        Object dartMarkerId = ConvertUtil.getKeyValueFromMapObject(markerToChange, "id");
        if (null != dartMarkerId) {
            MarkerController markerController = controllerMapByDartId.get(dartMarkerId);
            if (null != markerController) {
                MarkerUtil.interpretMarkerOptions(markerToChange, markerController);
            }
        }
    }


    private void removeByIdList(List<Object> markerIdsToRemove) {
        if (markerIdsToRemove == null) {
            return;
        }
        for (Object rawMarkerId : markerIdsToRemove) {
            if (rawMarkerId == null) {
                continue;
            }
            String markerId = (String) rawMarkerId;
            final MarkerController markerController = controllerMapByDartId.remove(markerId);
            if (markerController != null) {

                idMapByOverlyId.remove(markerController.getMarkerId());
                markerController.remove();
            }
        }
    }

    private void showMarkerInfoWindow(String dartMarkId) {
        MarkerController markerController = controllerMapByDartId.get(dartMarkId);
        if (null != markerController) {
            markerController.setIcon(getBitmapDescriptor(dartMarkId.equals(selectedMarkerDartId), markerController.getMarker().getTitle(), markerController.getMarker().getSnippet()));
            markerController.showInfoWindow();
        }
    }

    private void hideMarkerInfoWindow(String dartMarkId, LatLng newPosition) {
        if (TextUtils.isEmpty(dartMarkId)) {
            return;
        }
        if (!controllerMapByDartId.containsKey(dartMarkId)) {
            return;
        }
        MarkerController markerController = controllerMapByDartId.get(dartMarkId);
        if (null != markerController) {
            markerController.setIcon(getBitmapDescriptor(false, markerController.getMarker().getTitle(), markerController.getMarker().getSnippet()));

            if (null != newPosition && null != markerController.getPosition()) {
                if (markerController.getPosition().equals(newPosition)) {
                    return;
                }
            }
            markerController.hideInfoWindow();
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        hideMarkerInfoWindow(selectedMarkerDartId, null);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        String dartId = idMapByOverlyId.get(marker.getId());
        if (null == dartId) {
            return false;
        }
        final Map<String, Object> data = new HashMap<>(1);
        data.put("markerId", dartId);
        hideMarkerInfoWindow(selectedMarkerDartId, null);
        selectedMarkerDartId = dartId;
        showMarkerInfoWindow(dartId);
        methodChannel.invokeMethod("marker#onTap", data);
        LogUtil.i(CLASS_NAME, "onMarkerClick==>" + data);
        return true;
    }

    @Override
    public void onMarkerDragStart(Marker marker) {

    }

    @Override
    public void onMarkerDrag(Marker marker) {

    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        String markerId = marker.getId();
        String dartId = idMapByOverlyId.get(markerId);
        LatLng latLng = marker.getPosition();
        if (null == dartId) {
            return;
        }
        final Map<String, Object> data = new HashMap<>(2);
        data.put("markerId", dartId);
        data.put("position", ConvertUtil.latLngToList(latLng));
        methodChannel.invokeMethod("marker#onDragEnd", data);

        LogUtil.i(CLASS_NAME, "onMarkerDragEnd==>" + data);
    }

    @Override
    public void onPOIClick(Poi poi) {
        hideMarkerInfoWindow(selectedMarkerDartId, null != poi ? poi.getCoordinate() : null);
    }

}

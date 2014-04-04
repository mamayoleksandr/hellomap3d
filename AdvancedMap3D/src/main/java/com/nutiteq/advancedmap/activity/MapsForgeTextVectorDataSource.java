package com.nutiteq.advancedmap.activity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.reader.MapDatabase;
import org.mapsforge.map.reader.Way;
import org.mapsforge.map.reader.header.MapFileInfo;

import com.nutiteq.components.Bounds;
import com.nutiteq.components.CullState;
import com.nutiteq.components.Envelope;
import com.nutiteq.components.MapPos;
import com.nutiteq.geometry.Text;
import com.nutiteq.log.Log;
import com.nutiteq.projections.EPSG3857;
import com.nutiteq.style.StyleSet;
import com.nutiteq.style.TextStyle;
import com.nutiteq.utils.TileUtils;
import com.nutiteq.vectordatasources.AbstractVectorDataSource;

/**
 * Special text data source that uses MapsForgeRasterDataSource as input.
 * 
 * @author jaak
 *
 */
public abstract class MapsForgeTextVectorDataSource extends AbstractVectorDataSource<Text> {

//    public static class Feature {
//        public String type;
//        public String id;
//        public Geometry geometry;
//        public List<Tag> tags;
//        public String name;
//
//        public Feature() { }
//    }
    
//    private class DataSourceChangeListener implements OnChangeListener {
//        @Override
//        public void onElementChanged(VectorElement element) {
//            synchronized (MapsForgeTextVectorDataSource.this) {
//                Feature feature = (Feature) element.userData;
//                loadedElementMap.remove(feature.id);
//            }
//
//            notifyElementsChanged();
//        }
//
//        @Override
//        public void onElementsChanged() {
//            synchronized (MapsForgeTextVectorDataSource.this) {
//                loadedElementMap.clear();
//            }
//            
//            notifyElementsChanged();
//        }
//    }

    private final MapDatabase mapDatabase;
    private int maxElements = Integer.MAX_VALUE;
//    private Map<String, Text> loadedElementMap = new HashMap<String, Text>();
    
    public MapsForgeTextVectorDataSource(MapDatabase mapDatabase) {
        super(new EPSG3857());
        this.mapDatabase = mapDatabase;
        //dataSource.addOnChangeListener(new DataSourceChangeListener()); // TODO: bad practice, causes memory leak. Should use WeakRef listener. Or better, separate methods for create/destroy phase
    }

    public void setMaxElements(int maxElements) {
        this.maxElements = maxElements;
    }

    @Override
    public Envelope getDataExtent() {
        
        MapFileInfo mapFileInfo = this.mapDatabase.getMapFileInfo();
        if(mapFileInfo != null){
                // start position not defined, but boundingbox is defined
                MapPos boxMin = projection.fromWgs84(mapFileInfo.boundingBox.minLongitude, mapFileInfo.boundingBox.minLatitude);
                MapPos boxMax = projection.fromWgs84(mapFileInfo.boundingBox.maxLongitude, mapFileInfo.boundingBox.maxLatitude);
                return new Envelope(boxMin.x, boxMax.x, boxMin.y, boxMax.y);
            }
       
        return null;
    }

    @Override
    public Collection<Text> loadElements(CullState cullState) {
        long startTime = System.currentTimeMillis();
        int n = 0;
        synchronized (this) {
//            Map<String, Text> elementMap = new HashMap<String, Text>();
            List<Text> elementMap = new LinkedList<Text>();
            
            // calculate tile of cullState
            int zoom = cullState.zoom;
//            Envelope envelope = projection.fromInternal(cullState.envelope); // direct
            Envelope envelope = cullState.envelope; // tiledsource
//            Bounds bounds = projection.getBounds();
//            int zoomTiles = 1 * (1 << zoom);
//            double tileWidth  = bounds.getWidth()  / zoomTiles;
//            double tileHeight = bounds.getHeight() / zoomTiles;
////            
////            // Calculate tile extents
//            int tileX0 = (int) Math.floor(envelope.minX / tileWidth);
//            int tileY0 = (int) Math.floor(envelope.minY / tileHeight);
//            
            MapPos tile = TileUtils.MetersToTile(new MapPos(envelope.minX, envelope.minY), zoom); // direct
            
            // load tile from MapsForge file
            Log.debug("loading text tile " + tile + " zoom=" + zoom);
//            Log.debug("loading tile " + tileX0 +","+ tileY0 + " zoom=" + zoom);
            
            List<Way> ways = this.mapDatabase.readMapData(new Tile((int) tile.x, (int) tile.y, (byte) zoom)).ways; // direct
//            List<Way> ways = this.mapDatabase.readMapData(new Tile(tileX0, tileY0, (byte) zoom)).ways; // tiledsource
            
            for(Way way : ways){
//                Feature feature = new Feature();
                String name = null;
                String id = null;
                Tag type = null;
                // extract specific tags
                for (Tag tag: way.tags){
                   if(tag.key.equals("name")){
                       name = tag.value;
//                       Log.debug("way name = "+tag.value);
                   }else if(tag.key.equals("osm_id")){
                       // never happens sadly
                       id = tag.value;
                       Log.debug("way id = "+tag.value);
                       Log.debug("way name = "+name);
                   }else if(tag.key.equals("highway")){
                       type = new Tag(tag.key, tag.value);
                   }
                }

                // create text element
                Text element = createText(way, name, id, type, zoom);
                if (element != null) {
//                    Log.debug("adding text "+name+" type "+type.value);
                    element.attachToDataSource(this);
                    elementMap.add(element);
                    n++;
                }
                
                if(n > maxElements){
                    break;
                }
            }            

            long endTime = System.currentTimeMillis();
            Log.debug("MapsforgeTextDataSource: run time: " + (endTime-startTime) + " ms, texts: " + n);
            
            return elementMap;
        }
    }

    protected Text createText(Way way, String name, String id, Tag type, int zoom) {
        
        if (name == null) {
            return null;
        }
        if (name.trim().equals("")) {
            return null;
        }
        if(type == null){
            return null;
        }

        // Create base element based on geometry type
        Text.BaseElement baseElement = null;
        if (way.latLongs[0].length > 1) {
            List<MapPos> mapPoses = new ArrayList<MapPos>();
            // take always first segment of possible polyline here
            for (LatLong coords : way.latLongs[0]) {
                mapPoses.add(projection.fromWgs84(coords.longitude, coords.latitude));
            }
            baseElement = new Text.BaseLine(mapPoses);
        } else if(way.latLongs[0].length == 1){
            MapPos mapPos = projection.fromWgs84(way.latLongs[0][0].longitude, way.latLongs[0][0].latitude);
            baseElement = new Text.BasePoint(mapPos);
        } 
            
        // Create styleset for the feature
        StyleSet<TextStyle> styleSet = createFeatureStyleSet(way, zoom, type);
        if (styleSet == null) {
            return null;
        }

        // Create text. Put unique id to userdata field, that will be used to identify the element later
        return new Text(baseElement, name, styleSet, id);
    }

    protected abstract StyleSet<TextStyle> createFeatureStyleSet(Way way, int zoom, Tag type);

}

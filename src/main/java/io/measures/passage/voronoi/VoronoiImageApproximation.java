package io.measures.passage.voronoi;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import io.measures.passage.Sketch;
import io.measures.passage.geometry.Model3D;
import io.measures.passage.geometry.Point2D;
import io.measures.passage.geometry.Point3D;
import io.measures.passage.geometry.Triangle3D;
import megamu.mesh.MPolygon;
import megamu.mesh.Voronoi;
import processing.core.PImage;
import quickhull3d.Point3d;
import quickhull3d.QuickHull3D;

import static processing.core.PApplet.*;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * VoronoiImageApproximation
 * @author Dietrich Featherston
 */
public class VoronoiImageApproximation implements Model3D {

    private final Sketch s;
    private final Mode mode;
    private final PImage img;
    private final LinkedList<Point2D> available;
    private final LinkedList<Point2D> generatingPoints;
    private final Set<String> used;
    private final DecimalFormat df = new DecimalFormat("0.##");

    private MPolygon[] regions;
    private final float maxHeight;
    private Envelope imageBounds;
    private final ExecutorService threadpool;

    public VoronoiImageApproximation(Sketch s, Mode m, PImage img, int brightnessThreshold, int weighting, float maxHeight) {
        this.s = s;
        this.mode = m;
        this.img = img;
        this.imageBounds = new Envelope(0, img.width, 0, img.height);
        this.maxHeight = maxHeight;
        // pick appropriate pixels where a voronoi point could be added
        // giving weight to brighter pixels
        generatingPoints = Lists.newLinkedList();
        used = Sets.newHashSet();
        available = Lists.newLinkedList();
        if(weighting > 0) {
            for(int i = 0; i < img.width; i++) {
                for(int j = 0; j < img.height; j++) {
                    float density = mode.density(s, img, i, j);
                    int times = round(map(density, 0, 1, weighting, 1));
                    Point2D p = new Point2D(i, j);
                    for(int k = 0; k < times; k++) {
                        available.add(p);
                    }
                }
            }
        }
        else {
            for(int i = 0; i < img.width; i++) {
                for(int j = 0; j < img.height; j++) {
                    available.add(new Point2D(i, j));
                }
            }
        }

        // shuffle available points
        Collections.shuffle(available);

        int cpus = Runtime.getRuntime().availableProcessors();
        threadpool = new ThreadPoolExecutor(cpus, cpus, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(100000));
    }

    /**
     * @param n - number of steps to evolve
     */
    public void evolve(int n) {
        for(int i = 0; i < n; i++) {
            generatingPoints.add(pop());
        }
        update();
    }

    private Point2D pop() {
        Point2D p;
        String key;
        do {
            p = available.poll();
            key = df.format(p.x()) + ":" + df.format(p.y());
        } while(used.contains(key));
        used.add(key);
        return p;
    }

    private void update() {
        if(generatingPoints.isEmpty()) return;
        float[][] fpoints = new float[generatingPoints.size()][2];
        for(int i = 0; i < generatingPoints.size(); i++) {
            fpoints[i] = generatingPoints.get(i).toArray();
        }
        try {
            Voronoi v = new Voronoi(fpoints);
            regions = v.getRegions();
        }
        catch(Exception e) {
            println(e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    public MPolygon[] getRegions() {
        return regions;
    }

    public List<Point2D> getCentroids() {
        List<Point2D> out = Lists.newArrayListWithCapacity(regions.length);
        for(MPolygon p : regions) {
            out.add(p.getCentroid());
        }
        return out;
    }

    public float density(int x, int y) {
        return map(s.brightness(img.get(x, y)), 0, 255, 1, 0);
    }

    public List<Point2D> getWeightedCentroids() {
        GeometryFactory gf = new GeometryFactory();
        List<Point2D> out = Lists.newArrayListWithCapacity(regions.length);
        for(MPolygon p : regions) {
            if(!p.isEmpty()) {
                Coordinate[] coords = new Coordinate[p.getCoords().length+1];
                for(int i = 0; i < coords.length; i++) {
                    float[] c = p.getCoords()[i%p.getCoords().length];
                    coords[i] = new Coordinate(c[0], c[1]);
                }
                Polygon poly = gf.createPolygon(coords);
                List<Future<Point2D>> flist = Lists.newArrayList();
                flist.add(threadpool.submit(new CentroidCallable(poly, img)));
                for(Future<Point2D> f : flist) {
                    try {
                        Point2D point = f.get();
                        if(point != null) {
                            out.add(point);
                        }
                    } catch(Exception e) {
                        println(e.getMessage());
                        e.printStackTrace(System.err);
                    }
                }
            }
        }
        return out;
    }

    public List<Point2D> getGeneratingPoints() {
        return generatingPoints;
    }

    public void setGeneratingPoints(List<Point2D> in) {
        generatingPoints.clear();
        generatingPoints.addAll(in);
        update();
    }

    private float z(float x, float y) {
        return (s.brightness(img.get(round(x), round(y)))/255f) * maxHeight + s.random(0.0001f, 0.002f);
    }

    @Override
    public Iterable<Triangle3D> getTriangles() {
        List<Triangle3D> out = Lists.newArrayList();
        for(MPolygon p : regions) {
            float[][] coords = p.getCoords();
            if(coords.length == 3) {
                out.add(
                        new Triangle3D(
                                new Point3D(coords[0][0], coords[0][1], z(coords[0][0], coords[0][1])),
                                new Point3D(coords[1][0], coords[1][1], z(coords[1][0], coords[1][1])),
                                new Point3D(coords[2][0], coords[2][1], z(coords[2][0], coords[2][1]))
                        ));
            }
            else {
                QuickHull3D qh = new QuickHull3D(doubles(coords));
                qh.triangulate();
                Point3d[] vertices = qh.getVertices();
                int[][] faces = qh.getFaces(QuickHull3D.POINT_RELATIVE + QuickHull3D.CLOCKWISE);
                for(int i = 0; i < faces.length; i++) {
                    Point3d a = vertices[faces[i][0]];
                    Point3d b = vertices[faces[i][1]];
                    Point3d c = vertices[faces[i][2]];
                    out.add(
                            new Triangle3D(
                                    new Point3D((float)a.x, (float)a.y, z((float)a.x, (float)a.y)),
                                    new Point3D((float)b.x, (float)b.y, z((float)b.x, (float)b.y)),
                                    new Point3D((float)c.x, (float)c.y, z((float)c.x, (float)c.y))
                            ));
                }
            }
        }
        return out;
    }

    double[] doubles(float[][] f) {
        double[] d = new double[f.length*3];
        for(int i = 0; i < f.length; i++) {
            d[3*i  ] = f[i][0];
            d[3*i+1] = f[i][1];
            d[3*i+2] = z(f[i][0], f[i][1]);
        }
        return d;
    }

    public int getWidth() {
        return img.width;
    }

    public int getHeight() {
        return img.height;
    }

    private class CentroidCallable implements Callable<Point2D> {

        private final Polygon poly;
        private final PImage img;
        private final GeometryFactory gf;

        private CentroidCallable(Polygon p, PImage img) {
            this.poly = p;
            this.img = img;
            this.gf = new GeometryFactory();
        }

        @Override
        public Point2D call() throws Exception {
            Envelope e = poly.getEnvelopeInternal();
            if(imageBounds.contains(poly.getCentroid().getCoordinate())) {
                float sumx = 0f;
                float sumy = 0f;
                int count = 0;
                com.vividsolutions.jts.geom.Point cent = poly.getCentroid();
                for(int x = round((float) e.getMinX()); x < round((float)e.getMaxX()); x++) {
                    for(int y = round((float)e.getMinY()); y < round((float)e.getMaxY()); y++) {
                        if(poly.contains(gf.createPoint(new Coordinate(x, y)))) {
                            sumx += density(x, y) * (x-cent.getX());
                            sumy += density(x, y) * (y-cent.getY());
                            count++;
                        }
                    }
                }
                return new Point2D((float)cent.getX() + sumx/count, (float)cent.getY() + sumy/count);
            }
            return null;
        }
    }

    public enum Mode {
        whiteOnBlack {
            @Override
            public float density(Sketch s, PImage img, int x, int y) {
                return map(s.brightness(img.get(x, y)), 0, 255, 1, 0);
            }

            @Override
            public int getBackground() {
                return Color.BLACK.getRGB();
            }

            @Override
            public int getForeground() {
                return Color.WHITE.getRGB();
            }
        },
        blackOnWhite {
            @Override
            public float density(Sketch s, PImage img, int x, int y) {
                return map(s.brightness(img.get(x, y)), 0, 255, 0, 1);
            }

            @Override
            public int getBackground() {
                return Color.WHITE.getRGB();
            }

            @Override
            public int getForeground() {
                return Color.BLACK.getRGB();
            }
        };

        public abstract float density(Sketch s, PImage img, int x, int y);
        public abstract int getBackground();
        public abstract int getForeground();
    }
}

package com.d2fn.passage;

import joons.JoonsRenderer;
import processing.event.KeyEvent;

/**
 * JoonsSketch
 * @author Dietrich Featherston
 */
public class JoonsSketch extends Sketch {

    protected JoonsRenderer jr;
    protected boolean triggerRender = false;
    protected boolean waitingForRender = false;

    @Override
    public void setup() {
        initSize(P3D);
        jr = new JoonsRenderer(this);
        jr.setSampler("bucket");
        jr.setSizeMultiplier(1);
        jr.setAA(0, 2, 4);
        noiseDetail(3, 0.5f);
        randomSeed(0);
        noiseSeed(0);
    }

    @Override
    public void beforeFrame() {
        super.beforeFrame();
        jr.beginRecord();
    }

    @Override
    public void afterFrame() {
        super.afterFrame();
        jr.endRecord();
        jr.displayRendered(true);
        if(triggerRender && !jr.isRendering()) {
            triggerRender = false;
            waitingForRender = true;
            jr.render();
        }
        else if(jr.isRendered() && waitingForRender) {
            waitingForRender = false;
            snapshot();
        }
    }

    @Override
    public void keyPressed(KeyEvent event) {
        super.keyPressed(event);
        if (key == 'r' || key == 'R') {
            triggerRender = true;
        }
    }

    public void planeXY(float w, float h) {
        beginShape(QUADS);
        vertex(-w/2, -h/2, 0);
        vertex(-w/2,  h/2, 0);
        vertex( w/2,  h/2, 0);
        vertex( w/2, -h/2, 0);
        endShape();
    }

    public void planeYZ(float w, float h) {
        beginShape(QUADS);
        vertex(0, -w/2, -h/2);
        vertex(0, -w/2,  h/2);
        vertex(0,  w/2,  h/2);
        vertex(0,  w/2, -h/2);
        endShape();
    }

    public void diffuseFill(int color) {
        jr.fill("diffuse", red(color), green(color), blue(color));
    }

    public void glassFill(int color) {
        jr.fill("glass", red(color), green(color), blue(color));
    }

    public void shinyFill(int color, float shininess) {
        jr.fill("shiny", red(color), green(color), blue(color), shininess);
    }

    public void phongFill(int color) {
        jr.fill("phong", red(color), green(color), blue(color));
    }

    public void ambientOcclusionFill(int brightColor, int darkColor, float maxDist, int samples) {
        jr.fill("ambient_occlusion",
                red(brightColor), green(brightColor), blue(brightColor),
                red(darkColor), green(darkColor), blue(darkColor),
                maxDist, samples);
    }

    public void mirrorFill(int color) {
        jr.fill("mirror", red(color), green(color), blue(color));
    }

    public void lightFill() {
        jr.fill("light", 30, 30, 30, 64);
    }
}

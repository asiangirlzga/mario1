package com.blockhero.tv;

import java.util.ArrayList;
import java.util.Random;

/** Level data. Everything is built in code, so the APK needs no asset files. */
final class Level {
    static final int COUNT = 3;

    static final class Box {
        float x0, y0, z0, x1, y1, z1;
        float[] top, side;
        int axis = -1;               // -1 static, 0 = x, 1 = y, 2 = z
        float amp, spd, ph, prev;
        float dx, dy, dz;            // movement during the last update

        void shift(int a, float d) {
            if (a == 0) { x0 += d; x1 += d; }
            else if (a == 1) { y0 += d; y1 += d; }
            else { z0 += d; z1 += d; }
        }

        void update(float t) {
            float o = (float) Math.sin(t * spd + ph) * amp;
            float d = o - prev;
            prev = o;
            dx = dy = dz = 0;
            shift(axis, d);
            if (axis == 0) dx = d; else if (axis == 1) dy = d; else dz = d;
        }
    }

    static final class Enemy {
        float x, y, z, min, max, spd, squash, anim;
        float dir = 1;
        int axis;
        boolean gone;
    }

    static final class Coin { float x, y, z; boolean taken; }

    static final class Decor { float x, y, z, s; int kind; }

    static final class Post { float x, y, z; boolean reached; }

    // decor kinds
    static final int TREE = 0, BUSH = 1, CLOUD = 2, MOUNTAIN = 3, CACTUS = 4, CRYSTAL = 5, ROCK = 6;

    final ArrayList<Box> boxes = new ArrayList<>();
    final ArrayList<Enemy> enemies = new ArrayList<>();
    final ArrayList<Coin> coins = new ArrayList<>();
    final ArrayList<Decor> decor = new ArrayList<>();
    final ArrayList<Post> posts = new ArrayList<>();

    String name;
    int style;
    float[] sky, top, side, leafT, leafS, trunk, mtT, mtS, rock;
    float startX = 0, startY = 0, startZ = 0;
    float goalX, goalY, goalZ;
    float killY = -14f;
    float endZ;

    private float z, zs, lastTop;

    static float[] rgb(int hex) {
        return new float[]{((hex >> 16) & 255) / 255f, ((hex >> 8) & 255) / 255f, (hex & 255) / 255f};
    }

    static float[] shade(float[] c, float k) {
        return new float[]{Math.min(1f, c[0] * k), Math.min(1f, c[1] * k), Math.min(1f, c[2] * k)};
    }

    static Level make(int i) {
        Level l = new Level();
        if (i == 0) l.level0();
        else if (i == 1) l.level1();
        else l.level2();
        l.finish(i);
        return l;
    }

    // ---------------------------------------------------------------- builders

    private Box seg(float x, float w, float len, float topY) {
        Box b = new Box();
        b.x0 = x - w / 2; b.x1 = x + w / 2;
        b.z0 = z; b.z1 = z + len;
        b.y1 = topY; b.y0 = topY - 4f;
        b.top = top; b.side = side;
        boxes.add(b);
        zs = z;
        z += len;
        lastTop = topY;
        return b;
    }

    private void gap(float g) { z += g; }

    private void mover(Box b, int axis, float amp, float spd, float ph) {
        b.axis = axis; b.amp = amp; b.spd = spd; b.ph = ph;
        b.prev = (float) Math.sin(ph) * amp;
        b.shift(axis, b.prev);
    }

    /** Patrolling mushroom on the last segment. axis 0 walks along x at fixed z, axis 2 along z at fixed x. */
    private void en(int axis, float fixed, float min, float max, float spd) {
        Enemy e = new Enemy();
        e.axis = axis; e.min = min; e.max = max; e.spd = spd; e.y = lastTop;
        if (axis == 0) { e.x = min; e.z = fixed; } else { e.z = min; e.x = fixed; }
        enemies.add(e);
    }

    private void coins(float x0, float y0, float z0, float x1, float y1, float z1, int n) {
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0.5f : i / (float) (n - 1);
            Coin c = new Coin();
            c.x = x0 + (x1 - x0) * t; c.y = y0 + (y1 - y0) * t; c.z = z0 + (z1 - z0) * t;
            coins.add(c);
        }
    }

    private void arc(float x, float za, float zb, float y0, float h, int n) {
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            Coin c = new Coin();
            c.x = x; c.z = za + (zb - za) * t;
            c.y = y0 + h * (float) Math.sin(Math.PI * t);
            coins.add(c);
        }
    }

    private void post(float x, float zz) {
        Post p = new Post(); p.x = x; p.y = lastTop; p.z = zz; posts.add(p);
    }

    private void goal(float x, float zz) { goalX = x; goalY = lastTop; goalZ = zz; }

    // ------------------------------------------------------------------ levels

    private void level0() {
        name = "GRASS HILLS"; style = 0;
        sky = rgb(0x7EC8FF); top = rgb(0x5CC94A); side = rgb(0x8B5A2B);
        leafT = rgb(0x2FA03A); leafS = rgb(0x1F7A2A); trunk = rgb(0x7A4A25);
        mtT = rgb(0x8FB6A8); mtS = rgb(0x6E9588); rock = rgb(0x9A9A9A);
        z = -6;
        seg(0, 10, 22, 0);                      // -6 .. 16
        en(0, 12, -3, 3, 1.5f);
        coins(0, 1, 3, 0, 1, 11, 5);
        arc(0, 14.5f, 20.5f, 0.8f, 1.8f, 6);
        gap(3);
        seg(0, 10, 15, 0);                      // 19 .. 34
        en(0, 27, -3, 3, 1.8f);
        coins(0, 1, 22, 0, 1, 32, 5);
        arc(0, 33f, 38f, 0.8f, 1.8f, 5);
        gap(3);
        seg(0, 8, 6, 1);                        // 37 .. 43
        coins(0, 2.2f, 38, 0, 2.2f, 41, 3);
        seg(0, 6, 6, 2);
        coins(0, 3.2f, 44, 0, 3.2f, 47, 3);
        seg(0, 10, 22, 0);                      // 49 .. 71
        post(0, zs + 3);
        en(2, -2.5f, zs + 6, zs + 19, 1.8f);
        en(2, 2.5f, zs + 4, zs + 17, 2.0f);
        coins(0, 1, zs + 4, 0, 1, zs + 18, 6);
        gap(3);
        seg(-2, 5, 5, 0.5f);
        coins(-2, 1.7f, zs + 1.5f, -3, 1.7f, zs + 3.5f, 2);
        gap(2);
        seg(2, 5, 5, 1.0f);
        coins(2, 2.2f, zs + 1.5f, 2, 2.2f, zs + 3.5f, 2);
        gap(2);
        seg(-2, 5, 5, 1.5f);
        coins(-2, 2.7f, zs + 1.5f, -2, 2.7f, zs + 3.5f, 2);
        gap(2.5f);
        seg(0, 10, 22, 0);                      // after stepping stones
        post(0, zs + 3);
        en(0, zs + 8, -3.5f, 3.5f, 2.0f);
        en(0, zs + 13, -3.5f, 3.5f, 2.2f);
        en(2, 0, zs + 15, zs + 20, 1.6f);
        coins(0, 1, zs + 4, 0, 1, zs + 19, 7);
        gap(3);
        seg(0, 3, 12, 0);                       // narrow bridge
        coins(0, 1, zs + 1, 0, 1, zs + 11, 6);
        gap(3);
        seg(0, 12, 20, 0);                      // goal island
        goal(0, zs + 10);
        endZ = z;
    }

    private void level1() {
        name = "SUNSET DUNES"; style = 1;
        sky = rgb(0xFFA060); top = rgb(0xEBC873); side = rgb(0xB0693A);
        leafT = rgb(0x4FA64A); leafS = rgb(0x2F7A35); trunk = rgb(0x7A4A25);
        mtT = rgb(0xD98B5A); mtS = rgb(0xA8603C); rock = rgb(0xB59470);
        z = -6;
        seg(0, 10, 20, 0);                      // -6 .. 14
        en(0, 10, -3, 3, 2.0f);
        coins(0, 1, 2, 0, 1, 9, 5);
        gap(3.5f);
        seg(0, 6, 6, 0);
        arc(0, 13f, 18.5f, 0.8f, 2f, 6);
        gap(3.5f);
        seg(0, 6, 6, 0.5f);
        arc(0, 23f, 28.5f, 1.3f, 2f, 6);
        gap(2.5f);
        Box m = seg(0, 5, 5, 0.5f);             // sliding platform
        mover(m, 0, 3f, 1.3f, 0f);
        gap(2.5f);
        seg(0, 14, 16, 0.5f);
        post(0, zs + 2);
        en(0, zs + 6, -5, 5, 2.4f);
        en(0, zs + 11, -5, 5, 2.6f);
        coins(0, 1.5f, zs + 2, 0, 1.5f, zs + 14, 6);
        gap(3);
        seg(0, 6, 5, 1.7f);                     // stairs
        coins(0, 2.9f, zs + 1, 0, 2.9f, zs + 4, 2);
        seg(0, 6, 5, 2.9f);
        coins(0, 4.1f, zs + 1, 0, 4.1f, zs + 4, 2);
        seg(0, 6, 5, 4.1f);
        coins(0, 5.3f, zs + 1, 0, 5.3f, zs + 4, 2);
        gap(3.5f);
        seg(0, 8, 8, 2.0f);
        coins(0, 3, zs + 1, 0, 3, zs + 7, 4);
        gap(2.5f);
        Box e = seg(0, 5, 5, 2.0f);             // elevator
        mover(e, 1, 2f, 1.0f, 0f);
        gap(2.5f);
        seg(0, 12, 22, 2.0f);
        post(0, zs + 2);
        en(0, zs + 6, -4.5f, 4.5f, 2.6f);
        en(0, zs + 11, -4.5f, 4.5f, 2.8f);
        en(2, 0, zs + 14, zs + 20, 2.2f);
        coins(0, 3, zs + 2, 0, 3, zs + 20, 8);
        gap(3);
        seg(-2f, 4.5f, 3.5f, 2.0f);
        gap(2.5f);
        seg(2f, 4.5f, 3.5f, 2.0f);
        gap(2.5f);
        seg(-2f, 4.5f, 3.5f, 2.0f);
        gap(2.5f);
        seg(2f, 4.5f, 3.5f, 2.0f);
        gap(3);
        seg(0, 12, 20, 1.0f);
        goal(0, zs + 10);
        endZ = z;
    }

    private void level2() {
        name = "MOON NIGHT"; style = 2;
        sky = rgb(0x151A44); top = rgb(0x9CC8F0); side = rgb(0x4B4A9A);
        leafT = rgb(0x7FE8FF); leafS = rgb(0x3FB0E0); trunk = rgb(0x5A5AA0);
        mtT = rgb(0x3A3E8A); mtS = rgb(0x2A2C66); rock = rgb(0x6A6AA8);
        z = -6;
        seg(0, 8, 18, 0);                       // -6 .. 12
        en(0, 8, -2.5f, 2.5f, 2.2f);
        coins(0, 1, 2, 0, 1, 10, 5);
        gap(3.5f);
        seg(0, 3, 6, 0);                        // narrow beams
        arc(0, 12.5f, 18f, 0.8f, 2f, 5);
        gap(3.5f);
        seg(0, 3, 6, 1.0f);
        gap(5.5f);
        Box m = seg(0, 5, 5, 1.0f);             // slides along z
        mover(m, 2, 2.5f, 1.3f, 0f);
        gap(5.5f);
        seg(0, 10, 20, 1.0f);
        post(0, zs + 2);
        en(0, zs + 7, -4, 4, 2.8f);
        en(0, zs + 12, -4, 4, 3.0f);
        en(2, 0, zs + 15, zs + 19, 2.4f);
        coins(0, 2, zs + 2, 0, 2, zs + 18, 7);
        gap(3.5f);
        seg(-2, 4.5f, 4, 2.2f);                 // zig-zag climb
        coins(-2, 3.4f, zs + 1, -2, 3.4f, zs + 3, 2);
        gap(3);
        seg(2, 4.5f, 4, 3.4f);
        coins(2, 4.6f, zs + 1, 2, 4.6f, zs + 3, 2);
        gap(3);
        seg(-2, 4.5f, 4, 4.6f);
        coins(-2, 5.8f, zs + 1, -2, 5.8f, zs + 3, 2);
        gap(3.5f);
        seg(0, 7, 8, 3.0f);
        post(0, zs + 3);
        gap(2.5f);
        Box e = seg(0, 5, 5, 3.0f);             // elevator
        mover(e, 1, 2.5f, 1.1f, 1.5f);
        gap(2.5f);
        seg(0, 12, 24, 3.0f);
        en(0, zs + 5, -4.5f, 4.5f, 3.0f);
        en(0, zs + 10, -4.5f, 4.5f, 3.2f);
        en(0, zs + 15, -4.5f, 4.5f, 3.4f);
        en(2, 0, zs + 18, zs + 23, 2.6f);
        coins(0, 4, zs + 2, 0, 4, zs + 22, 8);
        gap(4);
        seg(0, 3, 14, 3.0f);                    // long beam with a guard
        en(2, 0, zs + 4, zs + 10, 2.0f);
        arc(0, zs + 0.5f, zs + 13.5f, 4f, 2.2f, 7);
        gap(4);
        seg(0, 12, 20, 3.0f);
        goal(0, zs + 10);
        endZ = z;
    }

    // ------------------------------------------------------------- decoration

    private void finish(int idx) {
        Random r = new Random(idx * 7919L + 13);
        for (Box b : boxes) {
            if (b.axis >= 0) continue;
            float w = b.x1 - b.x0;
            if (w < 9f) continue;
            for (float zz = b.z0 + 2.5f; zz < b.z1 - 1.5f; zz += 5f + r.nextFloat() * 4f) {
                if (r.nextInt(3) == 0) continue;
                float sd = r.nextBoolean() ? 1f : -1f;
                Decor d = new Decor();
                d.x = (b.x0 + b.x1) / 2 + sd * (w / 2 - 0.9f);
                d.y = b.y1; d.z = zz; d.s = 0.8f + r.nextFloat() * 0.6f;
                if (style == 0) d.kind = r.nextInt(3) == 0 ? BUSH : TREE;
                else if (style == 1) d.kind = r.nextInt(3) == 0 ? ROCK : CACTUS;
                else d.kind = r.nextInt(4) == 0 ? ROCK : CRYSTAL;
                decor.add(d);
            }
        }
        for (float zz = -20f; zz < endZ + 70f; zz += 36f) {
            for (int s = -1; s <= 1; s += 2) {
                Decor d = new Decor();
                d.kind = MOUNTAIN; d.x = s * (52f + r.nextFloat() * 22f); d.y = -10f; d.z = zz + r.nextFloat() * 14f;
                d.s = 1.0f + r.nextFloat() * 0.8f;
                decor.add(d);
            }
        }
        for (float zz = -10f; zz < endZ + 50f; zz += 20f) {
            Decor d = new Decor();
            d.kind = CLOUD; d.x = r.nextFloat() * 90f - 45f; d.y = 14f + r.nextFloat() * 9f; d.z = zz + r.nextFloat() * 10f;
            d.s = 0.8f + r.nextFloat();
            decor.add(d);
        }
    }
}

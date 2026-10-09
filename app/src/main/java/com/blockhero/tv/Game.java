package com.blockhero.tv;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The whole game: physics, rules and a tiny OpenGL ES 2.0 renderer.
 * One cube mesh (864 bytes) is drawn thousands of times with different matrices/colours,
 * so there are no textures, models or other assets: minimal RAM and storage.
 */
final class Game implements GLSurfaceView.Renderer {

    interface Hud { void set(String top, String center, boolean title); }

    static final int TITLE = 0, PLAY = 1, PAUSE = 2, CLEAR = 3, OVER = 4, WIN = 5;

    private static final float HX = 0.3f, HH = 1.25f;
    private static final float GRAV = -30f, JUMP = 12f, RUN = 6.5f;
    private static final float ENEMY_H = 0.95f;

    // ------------------------------------------------------- input (UI thread)
    volatile boolean kUp, kDown, kLeft, kRight, kJump;
    volatile boolean actPress, backPress, autoPause;
    volatile float axX, axY;
    volatile int state = TITLE;

    // ------------------------------------------------------------------ state
    private final Sfx sfx;
    private final Hud hud;
    private Level lv;
    private int levelIdx;
    private float time, stateT;

    private float px, py, pz, vx, vy, vz, yaw, walk, sq, inv, coyote, jumpBuf;
    private float cpX, cpY, cpZ, msgT;
    private boolean onGround, wasGround;
    private Level.Box ground;
    private int health = 3, lives = 3, coinCount, score;
    private String msg = "";
    private boolean hudDirty = true;
    private float camX, camY, camZ;
    private float[] cloudT, cloudS;
    private long last;

    // ------------------------------------------------------------------ colours
    private static final float[] RED = Level.rgb(0xD82020), BLUE = Level.rgb(0x2A4FD8),
            SKIN = Level.rgb(0xF0B98A), BROWN = Level.rgb(0x6B3A18), WHITE = Level.rgb(0xFFFFFF),
            BLACK = Level.rgb(0x151515), GOLD = Level.rgb(0xFFD21F), GREEN = Level.rgb(0x2FD060),
            SHADOW = Level.rgb(0x000000), GREY = Level.rgb(0xA8A8B0), CAPT = Level.rgb(0xB8652F),
            CAPS = Level.rgb(0x8B4A2B), STEM = Level.rgb(0xF2D2A4), CASTLE = Level.rgb(0xC9C2B4),
            DOOR = Level.rgb(0x4A2A14);

    // ------------------------------------------------------------------- GL
    private int prog, vbo, aPos, aNorm, uMVP, uCS, uTop, uSide, uFogC, uFog;
    private final float[] mProj = new float[16], mView = new float[16], mVP = new float[16],
            mM = new float[16], mMVP = new float[16];

    private static final String VS =
            "uniform mat4 uMVP; uniform vec2 uCS;\n" +
            "attribute vec3 aPos; attribute vec3 aNorm;\n" +
            "varying float vL; varying float vTop; varying float vW;\n" +
            "void main(){\n" +
            " vec3 n = vec3(uCS.x*aNorm.x + uCS.y*aNorm.z, aNorm.y, -uCS.y*aNorm.x + uCS.x*aNorm.z);\n" +
            " vL = 0.55 + 0.45*max(dot(n, vec3(0.43,0.77,-0.40)), 0.0);\n" +
            " vTop = step(0.5, n.y);\n" +
            " gl_Position = uMVP*vec4(aPos,1.0);\n" +
            " vW = gl_Position.w;\n" +
            "}";
    private static final String FS =
            "precision mediump float;\n" +
            "uniform vec3 uTop; uniform vec3 uSide; uniform vec3 uFogC; uniform vec2 uFog;\n" +
            "varying float vL; varying float vTop; varying float vW;\n" +
            "void main(){\n" +
            " vec3 c = mix(uSide, uTop, vTop) * vL;\n" +
            " float f = clamp((vW - uFog.x)/(uFog.y - uFog.x), 0.0, 1.0);\n" +
            " gl_FragColor = vec4(mix(c, uFogC, f), 1.0);\n" +
            "}";

    Game(Sfx sfx, Hud hud) {
        this.sfx = sfx;
        this.hud = hud;
    }

    // ============================================================= GL callbacks

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        int vs = compile(GLES20.GL_VERTEX_SHADER, VS);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, FS);
        prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vs);
        GLES20.glAttachShader(prog, fs);
        GLES20.glLinkProgram(prog);
        aPos = GLES20.glGetAttribLocation(prog, "aPos");
        aNorm = GLES20.glGetAttribLocation(prog, "aNorm");
        uMVP = GLES20.glGetUniformLocation(prog, "uMVP");
        uCS = GLES20.glGetUniformLocation(prog, "uCS");
        uTop = GLES20.glGetUniformLocation(prog, "uTop");
        uSide = GLES20.glGetUniformLocation(prog, "uSide");
        uFogC = GLES20.glGetUniformLocation(prog, "uFogC");
        uFog = GLES20.glGetUniformLocation(prog, "uFog");

        // unit cube, 36 vertices, interleaved position + normal
        int[][] faces = {
                {1, 0, 0, 0, 1, 0, 0, 0, 1}, {-1, 0, 0, 0, 0, 1, 0, 1, 0},
                {0, 1, 0, 0, 0, 1, 1, 0, 0}, {0, -1, 0, 1, 0, 0, 0, 0, 1},
                {0, 0, 1, 1, 0, 0, 0, 1, 0}, {0, 0, -1, 0, 1, 0, 1, 0, 0}};
        int[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, -1}, {1, 1}, {-1, 1}};
        float[] v = new float[36 * 6];
        int k = 0;
        for (int[] f : faces) {
            for (int[] c : corner) {
                for (int a = 0; a < 3; a++) v[k++] = 0.5f * f[a] + 0.5f * c[0] * f[3 + a] + 0.5f * c[1] * f[6 + a];
                for (int a = 0; a < 3; a++) v[k++] = f[a];
            }
        }
        FloatBuffer fb = ByteBuffer.allocateDirect(v.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(v).position(0);
        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        vbo = ids[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, v.length * 4, fb, GLES20.GL_STATIC_DRAW);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        last = System.nanoTime();
        if (lv == null) loadTitle();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        Matrix.perspectiveM(mProj, 0, 55f, w / (float) Math.max(1, h), 2f, 110f);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        float dt = (now - last) * 1e-9f;
        last = now;
        if (dt > 0.1f) dt = 0.1f;
        if (dt < 0f) dt = 0f;
        int n = (int) Math.ceil(dt / 0.017f);
        if (n < 1) n = 1;
        float h = dt / n;
        for (int i = 0; i < n; i++) step(h);
        if (hudDirty) {
            hudDirty = false;
            pushHud();
        }
        render();
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        return s;
    }

    // ============================================================ game flow

    private void loadTitle() {
        levelIdx = 0;
        lv = Level.make(0);
        setTheme();
        resetPlayer();
        state = TITLE;
        stateT = 0;
        sfx.music(false);
        hudDirty = true;
    }

    private void startLevel(int i) {
        levelIdx = i;
        lv = Level.make(i);
        setTheme();
        time = 0;
        resetPlayer();
        health = 3;
        state = PLAY;
        stateT = 0;
        msg = "LEVEL " + (i + 1) + "  " + lv.name;
        msgT = 2.2f;
        sfx.music(true);
        hudDirty = true;
    }

    private void setTheme() {
        if (lv.style == 2) {
            cloudT = Level.rgb(0x5A62A8);
            cloudS = Level.rgb(0x3E4486);
        } else {
            cloudT = WHITE;
            cloudS = Level.rgb(0xDCE6F5);
        }
    }

    private void resetPlayer() {
        px = lv.startX; py = lv.startY; pz = lv.startZ;
        cpX = px; cpY = py; cpZ = pz;
        vx = vy = vz = 0;
        yaw = 0; inv = 0; sq = 0; jumpBuf = 0; coyote = 0;
        onGround = false; wasGround = false; ground = null;
        camX = px; camY = py; camZ = pz;
    }

    private void onAction() {
        switch (state) {
            case TITLE:
                lives = 3; score = 0; coinCount = 0;
                startLevel(0);
                break;
            case PAUSE:
                state = PLAY;
                sfx.music(true);
                hudDirty = true;
                break;
            case CLEAR:
                if (stateT > 0.8f) {
                    if (levelIdx + 1 >= Level.COUNT) {
                        state = WIN; stateT = 0;
                        sfx.play(Sfx.WIN);
                        hudDirty = true;
                    } else {
                        startLevel(levelIdx + 1);
                    }
                }
                break;
            case OVER:
            case WIN:
                if (stateT > 0.8f) loadTitle();
                break;
            default:
                break;
        }
    }

    private void onBack() {
        if (state == PLAY) {
            state = PAUSE;
            sfx.music(false);
            hudDirty = true;
        } else if (state == PAUSE) {
            state = PLAY;
            sfx.music(true);
            hudDirty = true;
        } else if (state == CLEAR || state == OVER || state == WIN) {
            loadTitle();
        }
    }

    /** UI thread: returns true when the activity should close. */
    boolean backExit() {
        int s = state;
        if (s == TITLE || s == PAUSE) return true;
        backPress = true;
        return false;
    }

    private void hurt(float fromX, float fromZ) {
        if (inv > 0) return;
        health--;
        sfx.play(Sfx.HURT);
        hudDirty = true;
        if (health <= 0) {
            die();
            return;
        }
        inv = 1.6f;
        float dx = px - fromX, dz = pz - fromZ;
        float l = (float) Math.sqrt(dx * dx + dz * dz);
        if (l < 0.01f) { dx = 0; dz = -1; l = 1; }
        vx = dx / l * 6f; vz = dz / l * 6f; vy = 8f;
        onGround = false; ground = null;
    }

    private void die() {
        lives--;
        sfx.play(Sfx.HURT);
        hudDirty = true;
        if (lives <= 0) {
            state = OVER; stateT = 0;
            sfx.music(false);
            return;
        }
        px = cpX; py = cpY; pz = cpZ;
        vx = vy = vz = 0;
        health = 3; inv = 2f;
        onGround = false; ground = null;
        camX = px; camY = py; camZ = pz;
        msg = "OOPS!   " + lives + (lives == 1 ? " LIFE LEFT" : " LIVES LEFT");
        msgT = 1.6f;
    }

    // ============================================================ simulation

    private static float approach(float v, float target, float d) {
        if (v < target) return Math.min(v + d, target);
        return Math.max(v - d, target);
    }

    private void step(float h) {
        time += h;
        stateT += h;
        if (autoPause) {
            autoPause = false;
            if (state == PLAY) { state = PAUSE; hudDirty = true; }
        }
        if (backPress) {
            backPress = false;
            onBack();
        }
        boolean act = actPress;
        if (act) actPress = false;
        if (state == PLAY) {
            if (act) jumpBuf = 0.14f;
        } else if (act) {
            onAction();
        }

        if (state != PLAY) {
            if (state == TITLE) walk = 0;
            updateCamera(h);
            return;
        }

        if (msgT > 0) {
            msgT -= h;
            if (msgT <= 0) hudDirty = true;
        }

        for (Level.Box b : lv.boxes) if (b.axis >= 0) b.update(time);
        if (onGround && ground != null && ground.axis >= 0) {
            px += ground.dx; py += ground.dy; pz += ground.dz;
        }

        // ---- input -> velocity (screen right is world -X because we look down +Z)
        float ix = (kLeft ? 1f : 0f) - (kRight ? 1f : 0f) - axX;
        float iz = (kUp ? 1f : 0f) - (kDown ? 1f : 0f) - axY;
        float len = (float) Math.sqrt(ix * ix + iz * iz);
        if (len > 1f) { ix /= len; iz /= len; }
        float acc = onGround ? 70f : 40f;
        vx = approach(vx, ix * RUN, acc * h);
        vz = approach(vz, iz * RUN, acc * h);
        if (len > 0.1f) {
            float target = (float) Math.atan2(ix, iz);
            float d = target - yaw;
            while (d > Math.PI) d -= 2 * Math.PI;
            while (d < -Math.PI) d += 2 * Math.PI;
            yaw += d * Math.min(1f, 14f * h);
        }

        // ---- jump (buffered + coyote time + variable height)
        jumpBuf -= h;
        if (onGround) coyote = 0.1f; else coyote -= h;
        if (jumpBuf > 0 && coyote > 0) {
            vy = JUMP; jumpBuf = 0; coyote = 0;
            onGround = false; ground = null;
            sq = 0.3f;
            sfx.play(Sfx.JUMP);
        }
        vy += GRAV * h;
        if (!kJump && vy > 3f) vy -= 60f * h;
        if (vy < -26f) vy = -26f;

        // ---- move + collide, one axis at a time
        float vyPre = vy;
        onGround = false;
        ground = null;
        px += vx * h;
        pushOutHorizontal(true);
        pz += vz * h;
        pushOutHorizontal(false);
        py += vy * h;
        pushOutVertical();
        if (onGround && !wasGround && vyPre < -6f) sq = -0.28f;
        wasGround = onGround;
        sq += (0f - sq) * Math.min(1f, 12f * h);
        walk += (float) Math.sqrt(vx * vx + vz * vz) * h * 2.4f;
        if (inv > 0) inv -= h;

        // ---- enemies
        for (Level.Enemy e : lv.enemies) {
            if (e.gone) continue;
            if (e.squash > 0) {
                e.squash += h;
                if (e.squash > 0.6f) e.gone = true;
                continue;
            }
            e.anim += h;
            float d = e.spd * e.dir * h;
            if (e.axis == 0) {
                e.x += d;
                if (e.x > e.max) { e.x = e.max; e.dir = -1; }
                if (e.x < e.min) { e.x = e.min; e.dir = 1; }
            } else {
                e.z += d;
                if (e.z > e.max) { e.z = e.max; e.dir = -1; }
                if (e.z < e.min) { e.z = e.min; e.dir = 1; }
            }
            if (Math.abs(px - e.x) < 0.6f && Math.abs(pz - e.z) < 0.6f
                    && py < e.y + ENEMY_H && py + HH > e.y) {
                if (vy < 0 && py > e.y + 0.45f) {
                    e.squash = 0.001f;
                    vy = kJump ? 13.5f : 9.5f;
                    score += 100;
                    sq = 0.2f;
                    sfx.play(Sfx.STOMP);
                    hudDirty = true;
                } else {
                    hurt(e.x, e.z);
                }
            }
        }

        // ---- coins
        for (Level.Coin c : lv.coins) {
            if (c.taken) continue;
            float dx = px - c.x, dz = pz - c.z, dy = py + 0.6f - c.y;
            if (dx * dx + dz * dz < 0.7f && Math.abs(dy) < 1.1f) {
                c.taken = true;
                coinCount++;
                score += 10;
                if (coinCount >= 100) { coinCount -= 100; lives++; }
                sfx.play(Sfx.COIN);
                hudDirty = true;
            }
        }

        // ---- checkpoints
        for (Level.Post p : lv.posts) {
            if (!p.reached && Math.abs(px - p.x) < 1.6f && Math.abs(pz - p.z) < 1.6f && Math.abs(py - p.y) < 3f) {
                p.reached = true;
                cpX = p.x; cpY = p.y; cpZ = p.z;
                msg = "CHECKPOINT!";
                msgT = 1.2f;
                sfx.play(Sfx.COIN);
                hudDirty = true;
            }
        }

        // ---- goal
        float gx = px - lv.goalX, gz = pz - lv.goalZ;
        if (gx * gx + gz * gz < 1.7f && py < lv.goalY + 5f) {
            score += 500 + 100 * health;
            state = CLEAR;
            stateT = 0;
            sfx.music(false);
            sfx.play(Sfx.WIN);
            hudDirty = true;
        }

        if (py < lv.killY) die();

        updateCamera(h);
    }

    private boolean overlaps(Level.Box b) {
        return px - HX < b.x1 && px + HX > b.x0 && pz - HX < b.z1 && pz + HX > b.z0
                && py < b.y1 - 0.0001f && py + HH > b.y0 + 0.0001f;
    }

    private void pushOutHorizontal(boolean isX) {
        for (Level.Box b : lv.boxes) {
            if (!overlaps(b)) continue;
            if (isX) {
                px = px < (b.x0 + b.x1) * 0.5f ? b.x0 - HX - 0.001f : b.x1 + HX + 0.001f;
                vx = 0;
            } else {
                pz = pz < (b.z0 + b.z1) * 0.5f ? b.z0 - HX - 0.001f : b.z1 + HX + 0.001f;
                vz = 0;
            }
        }
    }

    private void pushOutVertical() {
        for (Level.Box b : lv.boxes) {
            if (!overlaps(b)) continue;
            if (py + HH * 0.5f > (b.y0 + b.y1) * 0.5f) {
                py = b.y1;
                if (vy < 0) vy = 0;
                onGround = true;
                ground = b;
            } else {
                py = b.y0 - HH;
                if (vy > 0) vy = 0;
            }
        }
    }

    private void updateCamera(float h) {
        camX += (px - camX) * Math.min(1f, 5f * h);
        camZ += (pz - camZ) * Math.min(1f, 8f * h);
        camY += (Math.max(py, -2f) - camY) * Math.min(1f, 3f * h);
    }

    private float groundBelow() {
        float best = -1000f;
        for (Level.Box b : lv.boxes) {
            if (px > b.x0 && px < b.x1 && pz > b.z0 && pz < b.z1 && b.y1 <= py + 0.3f && b.y1 > best) best = b.y1;
        }
        return best;
    }

    // ================================================================== HUD

    private void pushHud() {
        String top = "";
        String mid = "";
        if (state != TITLE) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < health; i++) sb.append('♥');
            sb.append("    LIVES ").append(lives)
                    .append("    COINS ").append(coinCount)
                    .append("    SCORE ").append(score)
                    .append("    ").append(levelIdx + 1).append('/').append(Level.COUNT)
                    .append("  ").append(lv.name);
            top = sb.toString();
        }
        switch (state) {
            case TITLE:
                mid = "BLOCK HERO 3D\n\nPress OK to start\n\nD-pad: move     OK: jump (hold for higher)\n"
                        + "Stomp the mushrooms, grab coins, reach the flag!";
                break;
            case PAUSE:
                mid = "PAUSED\n\nOK: resume     BACK: quit";
                break;
            case CLEAR:
                mid = "LEVEL CLEAR!\n\nScore " + score + "\nPress OK";
                break;
            case OVER:
                mid = "GAME OVER\n\nScore " + score + "\nPress OK";
                break;
            case WIN:
                mid = "YOU WIN!\n\nFinal score " + score + "\nPress OK";
                break;
            default:
                if (msgT > 0) mid = msg;
                break;
        }
        hud.set(top, mid, state == TITLE);
    }

    // ============================================================== rendering

    private float pc = 1, ps = 0, oX, oY, oZ, oYaw, oSY = 1, oSXZ = 1;

    private void setObj(float x, float y, float z, float yawA, float sy, float sxz) {
        oX = x; oY = y; oZ = z; oYaw = yawA; oSY = sy; oSXZ = sxz;
        pc = (float) Math.cos(yawA);
        ps = (float) Math.sin(yawA);
    }

    /** Box in the local space of the current object (origin at its feet, +z = forward). */
    private void part(float lx, float ly, float lz, float sx, float sy, float sz, float[] col) {
        float wx = oX + (lx * pc + lz * ps) * oSXZ;
        float wz = oZ + (-lx * ps + lz * pc) * oSXZ;
        box(wx, oY + ly * oSY, wz, sx * oSXZ, sy * oSY, sz * oSXZ, oYaw, col, col);
    }

    private void box(float cx, float cy, float cz, float sx, float sy, float sz, float yawA,
                     float[] top, float[] side) {
        Matrix.setIdentityM(mM, 0);
        Matrix.translateM(mM, 0, cx, cy, cz);
        float c = 1f, s = 0f;
        if (yawA != 0f) {
            Matrix.rotateM(mM, 0, yawA * 57.29578f, 0f, 1f, 0f);
            c = (float) Math.cos(yawA);
            s = (float) Math.sin(yawA);
        }
        Matrix.scaleM(mM, 0, sx, sy, sz);
        Matrix.multiplyMM(mMVP, 0, mVP, 0, mM, 0);
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mMVP, 0);
        GLES20.glUniform2f(uCS, c, s);
        GLES20.glUniform3f(uTop, top[0], top[1], top[2]);
        GLES20.glUniform3f(uSide, side[0], side[1], side[2]);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
    }

    private void render() {
        if (lv == null) return;
        float[] sky = lv.sky;
        GLES20.glClearColor(sky[0], sky[1], sky[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        float ox = state == TITLE ? (float) Math.sin(time * 0.5f) * 5f : 0f;
        Matrix.setLookAtM(mView, 0, camX + ox, camY + 5.0f, camZ - 9.0f, camX, camY + 1.2f, camZ + 4.0f, 0f, 1f, 0f);
        Matrix.multiplyMM(mVP, 0, mProj, 0, mView, 0);

        GLES20.glUseProgram(prog);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, 0);
        GLES20.glEnableVertexAttribArray(aNorm);
        GLES20.glVertexAttribPointer(aNorm, 3, GLES20.GL_FLOAT, false, 24, 12);
        GLES20.glUniform3f(uFogC, sky[0], sky[1], sky[2]);
        GLES20.glUniform2f(uFog, 38f, 95f);

        float zMin = camZ - 14f, zMax = camZ + 95f;

        for (Level.Box b : lv.boxes) {
            if (b.z1 < zMin || b.z0 > zMax) continue;
            box((b.x0 + b.x1) * 0.5f, (b.y0 + b.y1) * 0.5f, (b.z0 + b.z1) * 0.5f,
                    b.x1 - b.x0, b.y1 - b.y0, b.z1 - b.z0, 0f, b.top, b.side);
        }
        for (Level.Decor d : lv.decor) {
            if (d.z < zMin - 12f || d.z > zMax + 10f) continue;
            drawDecor(d);
        }
        for (Level.Coin c : lv.coins) {
            if (c.taken || c.z < zMin || c.z > zMax) continue;
            box(c.x, c.y + (float) Math.sin(time * 3f + c.z) * 0.1f, c.z, 0.55f, 0.55f, 0.12f, time * 3.5f, GOLD, GOLD);
        }
        for (Level.Enemy e : lv.enemies) {
            if (e.gone || e.z < zMin || e.z > zMax) continue;
            drawEnemy(e);
        }
        for (Level.Post p : lv.posts) {
            if (p.z < zMin || p.z > zMax) continue;
            box(p.x, p.y + 1.1f, p.z, 0.12f, 2.2f, 0.12f, 0f, WHITE, WHITE);
            box(p.x + 0.4f, p.y + 1.9f, p.z, 0.7f, 0.45f, 0.05f, 0f, p.reached ? GREEN : RED, p.reached ? GREEN : RED);
        }
        drawGoal();

        // player + blob shadow
        float gy = groundBelow();
        if (gy > -999f) {
            float sz = 0.9f - Math.min(0.4f, (py - gy) * 0.1f);
            box(px, gy + 0.05f, pz, sz, 0.04f, sz, 0f, SHADOW, SHADOW);
        }
        drawPlayer();
    }

    private void drawPlayer() {
        if (inv > 0 && ((int) (time * 14f) & 1) == 0) return;
        setObj(px, py, pz, yaw, 1f + sq, 1f - sq * 0.5f);
        float sp = (float) Math.sqrt(vx * vx + vz * vz);
        float amp = Math.min(1f, sp / 5f);
        float sw = (float) Math.sin(walk) * 0.2f * amp;
        float lift = onGround ? 0.08f * amp : 0f;
        float la = onGround ? sw : 0.18f;
        float lb = onGround ? -sw : -0.12f;
        // legs + shoes
        part(0.15f, 0.30f, la, 0.24f, 0.30f, 0.26f, BLUE);
        part(-0.15f, 0.30f, lb, 0.24f, 0.30f, 0.26f, BLUE);
        part(0.15f, 0.08f + Math.max(0f, sw) * lift * 4f, la + 0.04f, 0.27f, 0.16f, 0.38f, BROWN);
        part(-0.15f, 0.08f + Math.max(0f, -sw) * lift * 4f, lb + 0.04f, 0.27f, 0.16f, 0.38f, BROWN);
        // torso: overalls + shirt
        part(0f, 0.52f, 0f, 0.58f, 0.2f, 0.4f, BLUE);
        part(0f, 0.72f, 0f, 0.6f, 0.26f, 0.4f, RED);
        part(0f, 0.68f, 0.21f, 0.4f, 0.3f, 0.04f, BLUE);
        part(0.15f, 0.82f, 0.21f, 0.08f, 0.1f, 0.04f, GOLD);
        part(-0.15f, 0.82f, 0.21f, 0.08f, 0.1f, 0.04f, GOLD);
        // arms + gloves
        float as = onGround ? -sw : 0.25f;
        part(0.38f, 0.7f, as, 0.15f, 0.34f, 0.17f, RED);
        part(-0.38f, 0.7f, -as, 0.15f, 0.34f, 0.17f, RED);
        part(0.38f, 0.5f, as * 1.2f, 0.18f, 0.16f, 0.18f, WHITE);
        part(-0.38f, 0.5f, -as * 1.2f, 0.18f, 0.16f, 0.18f, WHITE);
        // head
        part(0f, 1.0f, 0f, 0.5f, 0.42f, 0.46f, SKIN);
        part(0f, 0.97f, 0.27f, 0.15f, 0.15f, 0.12f, SKIN);
        part(0f, 0.88f, 0.25f, 0.38f, 0.08f, 0.1f, BROWN);
        part(0.11f, 1.04f, 0.24f, 0.07f, 0.11f, 0.03f, BLACK);
        part(-0.11f, 1.04f, 0.24f, 0.07f, 0.11f, 0.03f, BLACK);
        part(0f, 1.02f, -0.2f, 0.5f, 0.2f, 0.1f, BROWN);
        // cap
        part(0f, 1.24f, 0f, 0.56f, 0.2f, 0.52f, RED);
        part(0f, 1.15f, 0.3f, 0.5f, 0.06f, 0.3f, RED);
        part(0f, 1.27f, 0.27f, 0.2f, 0.12f, 0.03f, WHITE);
    }

    private void drawEnemy(Level.Enemy e) {
        float yawE = e.axis == 0 ? (e.dir > 0 ? 1.5708f : -1.5708f) : (e.dir > 0 ? 0f : 3.14159f);
        float flat = e.squash > 0 ? 0.28f : 1f;
        setObj(e.x, e.y, e.z, yawE, flat, 1f);
        float st = (float) Math.sin(e.anim * 11f) * 0.12f;
        part(0.22f, 0.1f, st, 0.3f, 0.2f, 0.4f, BROWN);
        part(-0.22f, 0.1f, -st, 0.3f, 0.2f, 0.4f, BROWN);
        part(0f, 0.4f, 0f, 0.55f, 0.4f, 0.55f, STEM);
        float cy = 0.8f;
        box(oX, oY + cy * oSY, oZ, 0.98f, 0.45f * oSY, 0.98f, oYaw, CAPT, CAPS);
        part(0f, 1.05f, 0f, 0.35f, 0.06f, 0.35f, WHITE);
        part(0.15f, 0.45f, 0.29f, 0.2f, 0.2f, 0.05f, WHITE);
        part(-0.15f, 0.45f, 0.29f, 0.2f, 0.2f, 0.05f, WHITE);
        part(0.13f, 0.43f, 0.32f, 0.08f, 0.12f, 0.04f, BLACK);
        part(-0.13f, 0.43f, 0.32f, 0.08f, 0.12f, 0.04f, BLACK);
        part(0f, 0.6f, 0.3f, 0.5f, 0.06f, 0.05f, BLACK);
    }

    private void drawGoal() {
        float gx = lv.goalX, gy = lv.goalY, gz = lv.goalZ;
        if (gz < camZ - 14f || gz > camZ + 95f) return;
        box(gx, gy + 0.3f, gz, 1.2f, 0.6f, 1.2f, 0f, GREY, GREY);
        box(gx, gy + 2.7f, gz, 0.16f, 4.8f, 0.16f, 0f, WHITE, WHITE);
        box(gx, gy + 5.2f, gz, 0.4f, 0.4f, 0.4f, 0f, GOLD, GOLD);
        box(gx + 0.7f + (float) Math.sin(time * 4f) * 0.08f, gy + 4.4f, gz, 1.3f, 0.8f, 0.06f, 0f, GREEN, GREEN);
        // little castle behind the flag
        box(gx, gy + 1.8f, gz + 5f, 5f, 3.6f, 3f, 0f, CASTLE, CASTLE);
        box(gx - 2f, gy + 4.0f, gz + 5f, 1f, 0.8f, 3f, 0f, CASTLE, CASTLE);
        box(gx, gy + 4.0f, gz + 5f, 1f, 0.8f, 3f, 0f, CASTLE, CASTLE);
        box(gx + 2f, gy + 4.0f, gz + 5f, 1f, 0.8f, 3f, 0f, CASTLE, CASTLE);
        box(gx, gy + 1.0f, gz + 3.45f, 1.3f, 2.0f, 0.1f, 0f, DOOR, DOOR);
        box(gx, gy + 4.9f, gz + 5f, 0.12f, 1.2f, 0.12f, 0f, WHITE, WHITE);
        box(gx + 0.45f, gy + 5.3f, gz + 5f, 0.8f, 0.4f, 0.05f, 0f, RED, RED);
    }

    private void drawDecor(Level.Decor d) {
        float s = d.s, x = d.x, y = d.y, z = d.z;
        switch (d.kind) {
            case Level.TREE:
                box(x, y + 0.8f * s, z, 0.5f * s, 1.6f * s, 0.5f * s, 0f, lv.trunk, lv.trunk);
                box(x, y + 1.9f * s, z, 2.0f * s, 1.0f * s, 2.0f * s, 0f, lv.leafT, lv.leafS);
                box(x, y + 2.75f * s, z, 1.4f * s, 0.8f * s, 1.4f * s, 0f, lv.leafT, lv.leafS);
                box(x, y + 3.4f * s, z, 0.8f * s, 0.6f * s, 0.8f * s, 0f, lv.leafT, lv.leafS);
                break;
            case Level.BUSH:
                box(x, y + 0.35f * s, z, 1.3f * s, 0.7f * s, 1.1f * s, 0f, lv.leafT, lv.leafS);
                box(x + 0.5f * s, y + 0.25f * s, z + 0.3f * s, 0.8f * s, 0.5f * s, 0.8f * s, 0f, lv.leafT, lv.leafS);
                break;
            case Level.CACTUS:
                box(x, y + 1.1f * s, z, 0.55f * s, 2.2f * s, 0.55f * s, 0f, lv.leafT, lv.leafS);
                box(x + 0.45f * s, y + 1.2f * s, z, 0.9f * s, 0.3f * s, 0.3f * s, 0f, lv.leafT, lv.leafS);
                box(x + 0.75f * s, y + 1.6f * s, z, 0.3f * s, 0.8f * s, 0.3f * s, 0f, lv.leafT, lv.leafS);
                break;
            case Level.ROCK:
                box(x, y + 0.35f * s, z, 1.2f * s, 0.7f * s, 1.0f * s, 0.4f, lv.rock, lv.rock);
                box(x + 0.5f * s, y + 0.2f * s, z + 0.4f * s, 0.6f * s, 0.4f * s, 0.6f * s, 0.9f, lv.rock, lv.rock);
                break;
            case Level.CRYSTAL:
                box(x, y + 1.4f * s, z, 0.6f * s, 2.8f * s, 0.6f * s, 0.6f, lv.leafT, lv.leafS);
                box(x + 0.5f * s, y + 0.8f * s, z + 0.3f * s, 0.4f * s, 1.6f * s, 0.4f * s, 0.2f, lv.leafT, lv.leafS);
                break;
            case Level.CLOUD:
                box(x, y, z, 4f * s, 1.1f * s, 2.4f * s, 0f, cloudT, cloudS);
                box(x + 1.6f * s, y + 0.35f * s, z + 0.2f * s, 2.4f * s, 1.0f * s, 1.8f * s, 0f, cloudT, cloudS);
                box(x - 1.8f * s, y - 0.1f * s, z, 2.2f * s, 0.8f * s, 1.6f * s, 0f, cloudT, cloudS);
                break;
            default: // MOUNTAIN
                box(x, y + 3.5f * s, z, 24f * s, 7f * s, 24f * s, 0f, lv.mtT, lv.mtS);
                box(x, y + 10.5f * s, z, 16f * s, 7f * s, 16f * s, 0f, lv.mtT, lv.mtS);
                box(x, y + 17.5f * s, z, 9f * s, 7f * s, 9f * s, 0f, lv.mtT, lv.mtS);
                box(x, y + 23.5f * s, z, 4f * s, 5f * s, 4f * s, 0f, WHITE, lv.mtS);
                break;
        }
    }
}

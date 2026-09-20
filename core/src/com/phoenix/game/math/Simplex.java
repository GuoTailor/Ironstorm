package com.phoenix.game.math;

import java.util.Random;

/**
 * 二维 Simplex 噪声，最小实现（参照 arc.util.noise.Simplex / Stefan Gustavson 的经典实现移植）。
 * <p>libgdx 没有内置 Simplex 噪声，矿脉生成需要比 World 内置值噪声更自然的团块分布，
 * 故按最小实现复制（对应原版 BasicGenerator 对 arc Simplex 的用法）。
 */
public class Simplex{
    //二维偏斜因子：(√3 - 1) / 2（对应 arc Simplex.raw_noise_2d 里的 F2）
    private static final float F2 = (float)((Math.sqrt(3.0) - 1.0) / 2.0);
    private static final float G2 = (3.0f - (float)Math.sqrt(3.0)) / 6.0f;
    private static final int[][] GRAD = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final int[] perm = new int[512];

    public Simplex(int seed){
        setSeed(seed);
    }

    public void setSeed(int seed){
        Random rand = new Random(seed);
        int[] p = new int[256];
        for(int i = 0; i < 256; i++) p[i] = i;
        for(int i = 255; i > 0; i--){
            int j = rand.nextInt(i + 1);
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for(int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    /** 原始 2D Simplex 噪声，返回约 [-1, 1]。 */
    public float noise(float xin, float yin){
        float s = (xin + yin) * F2;
        int i = (int)Math.floor(xin + s), j = (int)Math.floor(yin + s);
        float t = (i + j) * G2;
        float x0 = xin - (i - t), y0 = yin - (j - t);

        int i1, j1;
        if(x0 > y0){ i1 = 1; j1 = 0; }else{ i1 = 0; j1 = 1; }

        float x1 = x0 - i1 + G2, y1 = y0 - j1 + G2;
        float x2 = x0 - 1f + 2f * G2, y2 = y0 - 1f + 2f * G2;
        int ii = i & 255, jj = j & 255;

        float n = 0;

        float t0 = 0.5f - x0 * x0 - y0 * y0;
        if(t0 > 0){
            t0 *= t0;
            n += t0 * t0 * dot(GRAD[perm[ii + perm[jj]] & 7], x0, y0);
        }
        float t1 = 0.5f - x1 * x1 - y1 * y1;
        if(t1 > 0){
            t1 *= t1;
            n += t1 * t1 * dot(GRAD[perm[ii + i1 + perm[jj + j1]] & 7], x1, y1);
        }
        float t2 = 0.5f - x2 * x2 - y2 * y2;
        if(t2 > 0){
            t2 *= t2;
            n += t2 * t2 * dot(GRAD[perm[ii + 1 + perm[jj + 1]] & 7], x2, y2);
        }

        return 70f * n;
    }

    /**
     * 分形叠加，结果归一化到 [0, 1]（对应 arc 的 octaveNoise2D 语义）。
     * @param octaves 叠加层数（每层频率翻倍）
     * @param falloff 每层振幅衰减
     * @param scl 尺度（值越小矿团越大；对应原版传入 1f / (40 + i * 2) 的用法）
     */
    public float octaveNoise2D(int octaves, float falloff, float scl, float x, float y){
        float total = 0, amplitude = 1, max = 0;
        for(int k = 0; k < octaves; k++){
            total += noise(x * scl, y * scl) * amplitude;
            max += amplitude;
            amplitude *= falloff;
            scl *= 2f;
        }
        return (total / max) * 0.5f + 0.5f;
    }

    private static float dot(int[] g, float x, float y){
        return g[0] * x + g[1] * y;
    }
}

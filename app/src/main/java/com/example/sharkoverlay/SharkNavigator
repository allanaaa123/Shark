
package com.example.sharkoverlay

import kotlin.math.*
import kotlin.random.Random

/**
 * 雙髻鯊共用導航引擎。
 * 巡航、提前轉彎、完成轉彎後恢復直游。
 * 座標與速度單位為像素。
 */
class SharkNavigator(
    var x: Float,
    var y: Float,
    var angle: Float,
    private val random: Random = Random.Default
) {
    private enum class Mode {
        CRUISE,
        TURN
    }

    private var mode = Mode.CRUISE
    private var turnRemaining = 0f
    private var turnSign = 1f
    private var cruiseRemaining = 0f

    var phase = random.nextFloat() * 6.283185f
        private set

    fun update(
        dtSeconds: Float,
        width: Float,
        height: Float,
        speed: Float,
        radius: Float
    ) {
        if (width <= 0f || height <= 0f) return

        val dt = dtSeconds.coerceIn(0f, 0.08f)
        val v = speed.coerceAtLeast(1f)
        val r = radius.coerceAtLeast(1f)

        val safeX = r.coerceAtMost(width * 0.45f)
        val safeY = r.coerceAtMost(height * 0.45f)

        val left = safeX
        val right = width - safeX
        val top = safeY
        val bottom = height - safeY

        val heading = Math.toRadians(angle.toDouble())
        val vx = cos(heading).toFloat()
        val vy = sin(heading).toFloat()

        // 提前預測是否會接近螢幕邊界
        val lookAhead = (
            v * 1.5f + r * 1.7f
        ).coerceAtMost(min(width, height) * 0.45f)

        val predictedX = x + vx * lookAhead
        val predictedY = y + vy * lookAhead

        val threatened =
            predictedX < left ||
            predictedX > right ||
            predictedY < top ||
            predictedY > bottom

        if (mode == Mode.CRUISE) {
            cruiseRemaining -= v * dt

            if (threatened) {
                // 比較左轉與右轉哪個更能遠離邊界
                val plusAngle = Math.toRadians(
                    (angle + 90f).toDouble()
                )
                val minusAngle = Math.toRadians(
                    (angle - 90f).toDouble()
                )

                val cx = width * 0.5f - x
                val cy = height * 0.5f - y

                val plusScore =
                    cos(plusAngle) * cx +
                    sin(plusAngle) * cy

                val minusScore =
                    cos(minusAngle) * cx +
                    sin(minusAngle) * cy

                turnSign =
                    if (abs(plusScore - minusScore) < 0.001) {
                        if (random.nextBoolean()) 1f else -1f
                    } else if (plusScore > minusScore) {
                        1f
                    } else {
                        -1f
                    }

                // 一次轉約 145～180 度
                turnRemaining =
                    145f + random.nextFloat() * 35f

                mode = Mode.TURN

            } else if (cruiseRemaining <= 0f) {
                // 偶爾小幅度改變游泳方向
                if (random.nextFloat() < 0.25f) {
                    turnSign =
                        if (random.nextBoolean()) 1f else -1f

                    turnRemaining =
                        15f + random.nextFloat() * 30f

                    mode = Mode.TURN
                }

                cruiseRemaining = max(
                    80f,
                    v * (2f + random.nextFloat() * 3f)
                )
            }
        }

        if (mode == Mode.TURN) {
            // 速度越快，轉彎半徑越大
            val turnRadius = max(
                r * 1.4f,
                v * 0.9f
            )

            val degreesPerSecond = Math.toDegrees(
                (v / turnRadius).toDouble()
            ).toFloat().coerceIn(25f, 110f)

            val step = min(
                turnRemaining,
                degreesPerSecond * dt
            )

            angle = (
                angle + turnSign * step + 360f
            ) % 360f

            turnRemaining -= step

            if (turnRemaining <= 0.01f) {
                mode = Mode.CRUISE

                // 轉完後必須直游一段距離
                cruiseRemaining = max(
                    100f,
                    v * (2f + random.nextFloat() * 2f)
                )
            }
        }

        // 按目前方向前進
        val a = Math.toRadians(angle.toDouble())

        x += cos(a).toFloat() * v * dt
        y += sin(a).toFloat() * v * dt

        // 最後的邊界保護
        x = x.coerceIn(left, right)
        y = y.coerceIn(top, bottom)

        // 甩尾動畫進度
        phase += dt * (4f + v / 25f)
    }
}

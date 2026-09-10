package th.ac.vu.classflow.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.model.DomainResult;

public final class RadarChartView extends View {

    private static final int NUM_AXES = 5;
    private static final double MIN_SCORE = 1.0;
    private static final double MAX_SCORE = 4.0;

    private final Paint webPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint polyFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint polyStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint missingPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    @Nullable
    private List<DomainResult> domainResults;

    public RadarChartView(Context context) {
        super(context);
        init(context);
    }

    public RadarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RadarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        int colorPrimary = ContextCompat.getColor(context, R.color.indigo_primary);
        int colorWeb = ContextCompat.getColor(context, R.color.slate_light);
        int colorSlate = ContextCompat.getColor(context, R.color.slate_gray);

        webPaint.setColor(colorWeb);
        webPaint.setStyle(Paint.Style.STROKE);
        webPaint.setStrokeWidth(dp(1.2f));

        spokePaint.setColor(colorWeb);
        spokePaint.setStyle(Paint.Style.STROKE);
        spokePaint.setStrokeWidth(dp(1.2f));

        polyFillPaint.setColor(Color.argb(45, Color.red(colorPrimary), Color.green(colorPrimary), Color.blue(colorPrimary)));
        polyFillPaint.setStyle(Paint.Style.FILL);

        polyStrokePaint.setColor(colorPrimary);
        polyStrokePaint.setStyle(Paint.Style.STROKE);
        polyStrokePaint.setStrokeWidth(dp(2.5f));

        pointPaint.setColor(colorPrimary);
        pointPaint.setStyle(Paint.Style.FILL);

        pointInnerPaint.setColor(Color.WHITE);
        pointInnerPaint.setStyle(Paint.Style.FILL);

        missingPointPaint.setColor(colorSlate);
        missingPointPaint.setStyle(Paint.Style.STROKE);
        missingPointPaint.setStrokeWidth(dp(1.5f));
        missingPointPaint.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(3)}, 0));

        labelPaint.setColor(ContextCompat.getColor(context, R.color.indigo_primary_dark));
        labelPaint.setTextSize(sp(10.5f));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);

        scorePaint.setColor(colorSlate);
        scorePaint.setTextSize(sp(9.5f));
        scorePaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setDomainResults(@Nullable List<DomainResult> results) {
        this.domainResults = results != null ? new ArrayList<>(results) : null;
        invalidate();
    }

    public void setData(@Nullable th.ac.vu.classflow.data.model.AssessmentCalculation calculation) {
        setDomainResults(calculation != null ? calculation.getDomainResults() : null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desiredHeight = Math.max(width, (int) dp(260));
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        float centerX = width / 2f;
        float centerY = height / 2f;
        float maxRadius = Math.min(width, height) * 0.32f;
        float innerOffset = maxRadius * 0.20f; // radius for score 1.0

        // 1. Draw web concentric pentagons for scale 1, 2, 3, 4
        for (int step = 1; step <= 4; step++) {
            float ratio = (step - 1f) / 3f;
            float r = innerOffset + ratio * (maxRadius - innerOffset);
            drawWebPolygon(canvas, centerX, centerY, r);
        }

        // 2. Draw 5 spokes from center to maxRadius
        for (int i = 0; i < NUM_AXES; i++) {
            double angle = -Math.PI / 2.0 + i * (2.0 * Math.PI / NUM_AXES);
            float endX = (float) (centerX + maxRadius * Math.cos(angle));
            float endY = (float) (centerY + maxRadius * Math.sin(angle));
            canvas.drawLine(centerX, centerY, endX, endY, spokePaint);

            // Draw axis labels
            drawAxisLabel(canvas, i, centerX, centerY, maxRadius);
        }

        if (domainResults == null || domainResults.size() < NUM_AXES) {
            return;
        }

        // 3. Compute coordinates for each axis
        PointF[] points = new PointF[NUM_AXES];
        int availableCount = 0;

        for (int i = 0; i < NUM_AXES; i++) {
            DomainResult dr = domainResults.get(i);
            if (dr.isAvailable() && dr.getScore() != null) {
                double score = Math.max(MIN_SCORE, Math.min(MAX_SCORE, dr.getScore()));
                double ratio = (score - MIN_SCORE) / (MAX_SCORE - MIN_SCORE);
                float r = innerOffset + (float) ratio * (maxRadius - innerOffset);

                double angle = -Math.PI / 2.0 + i * (2.0 * Math.PI / NUM_AXES);
                float x = (float) (centerX + r * Math.cos(angle));
                float y = (float) (centerY + r * Math.sin(angle));
                points[i] = new PointF(x, y);
                availableCount++;
            } else {
                // Critical: Missing/insufficient score is NOT plotted at 0.
                points[i] = null;
            }
        }

        // 4. Draw polygon
        if (availableCount == NUM_AXES) {
            // All 5 available: closed filled polygon
            path.reset();
            path.moveTo(points[0].x, points[0].y);
            for (int i = 1; i < NUM_AXES; i++) {
                path.lineTo(points[i].x, points[i].y);
            }
            path.close();
            canvas.drawPath(path, polyFillPaint);
            canvas.drawPath(path, polyStrokePaint);
        } else if (availableCount >= 3) {
            // Connect contiguous available segments without connecting to missing axes or center (0,0)
            for (int i = 0; i < NUM_AXES; i++) {
                int next = (i + 1) % NUM_AXES;
                if (points[i] != null && points[next] != null) {
                    canvas.drawLine(points[i].x, points[i].y, points[next].x, points[next].y, polyStrokePaint);
                }
            }
        }

        // 5. Draw point markers
        for (int i = 0; i < NUM_AXES; i++) {
            PointF p = points[i];
            if (p != null) {
                canvas.drawCircle(p.x, p.y, dp(5f), pointPaint);
                canvas.drawCircle(p.x, p.y, dp(2.5f), pointInnerPaint);
            } else {
                // Distinct N/O indicator at outer web margin
                double angle = -Math.PI / 2.0 + i * (2.0 * Math.PI / NUM_AXES);
                float nx = (float) (centerX + (innerOffset * 0.7f) * Math.cos(angle));
                float ny = (float) (centerY + (innerOffset * 0.7f) * Math.sin(angle));
                canvas.drawCircle(nx, ny, dp(3.5f), missingPointPaint);
            }
        }
    }

    private void drawWebPolygon(Canvas canvas, float cx, float cy, float radius) {
        path.reset();
        for (int i = 0; i < NUM_AXES; i++) {
            double angle = -Math.PI / 2.0 + i * (2.0 * Math.PI / NUM_AXES);
            float x = (float) (cx + radius * Math.cos(angle));
            float y = (float) (cy + radius * Math.sin(angle));
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        canvas.drawPath(path, webPaint);
    }

    private void drawAxisLabel(Canvas canvas, int index, float cx, float cy, float maxRadius) {
        double angle = -Math.PI / 2.0 + index * (2.0 * Math.PI / NUM_AXES);
        float labelDistance = maxRadius + dp(20);

        float lx = (float) (cx + labelDistance * Math.cos(angle));
        float ly = (float) (cy + labelDistance * Math.sin(angle));

        String labelText;
        switch (index) {
            case 0: labelText = "Coding"; break;
            case 1: labelText = "Logic"; break;
            case 2: labelText = "Build & Debug"; break;
            case 3: labelText = "Ownership"; break;
            case 4: labelText = "Independence"; break;
            default: labelText = ""; break;
        }

        String scoreText = "";
        if (domainResults != null && index < domainResults.size()) {
            DomainResult dr = domainResults.get(index);
            if (dr.isAvailable() && dr.getScore() != null) {
                scoreText = String.format(Locale.US, "%.2f", dr.getScore());
            } else {
                scoreText = "N/O";
            }
        }

        // Small vertical offset adjustment based on position
        float labelY = ly;
        if (index == 0) {
            labelY -= dp(6);
        } else if (index == 2 || index == 3) {
            labelY += dp(8);
        }

        canvas.drawText(labelText, lx, labelY, labelPaint);
        if (!scoreText.isEmpty()) {
            canvas.drawText(scoreText, lx, labelY + dp(12), scorePaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}

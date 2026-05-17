package com.example.digitalmathsolver;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.nex3z.fingerpaintview.FingerPaintView;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // The two boxes where the user draws digits
    private FingerPaintView boxOne;
    private FingerPaintView boxTwo;

    // Text areas for equation and confidence scores
    private TextView resultText;
    private TextView confidenceOneText;
    private TextView confidenceTwoText;

    // Stores the selected operation
    private String operation = "+";

    // Runs the TensorFlow Lite model
    private Interpreter interpreter;

    // Stores confidence scores from the model
    private float confidenceOne;
    private float confidenceTwo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Loads the XML screen
        setContentView(R.layout.activity_main);

        // Connects the XML drawing boxes to Java
        boxOne = findViewById(R.id.boxOne);
        boxTwo = findViewById(R.id.boxTwo);

        // Connects the text areas to Java
        resultText = findViewById(R.id.resultText);
        confidenceOneText = findViewById(R.id.confidenceOneText);
        confidenceTwoText = findViewById(R.id.confidenceTwoText);

        // Connects all buttons to Java
        Button addButton = findViewById(R.id.addButton);
        Button subtractButton = findViewById(R.id.subtractButton);
        Button multiplyButton = findViewById(R.id.multiplyButton);
        Button divideButton = findViewById(R.id.divideButton);
        Button calculateButton = findViewById(R.id.calculateButton);
        Button clearButton = findViewById(R.id.clearButton);

        // Creates the drawing pen
        Paint p = new Paint();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(28);
        p.setColor(Color.BLACK);

        // Applies the pen to both drawing boxes
        boxOne.setPen(p);
        boxTwo.setPen(p);

        // Loads the digit recognition model
        try {
            interpreter = new Interpreter(loadModelFile("digit.tflite"));
        } catch (IOException e) {
            resultText.setText("Model failed to load");
        }

        // Operation buttons change the selected operation
        addButton.setOnClickListener(v -> operation = "+");
        subtractButton.setOnClickListener(v -> operation = "-");
        multiplyButton.setOnClickListener(v -> operation = "×");
        divideButton.setOnClickListener(v -> operation = "÷");

        // Calculate button runs the prediction and math
        calculateButton.setOnClickListener(v -> calculateResult());

        // Clear button clears both boxes and resets the text
        clearButton.setOnClickListener(v -> {
            boxOne.clear();
            boxTwo.clear();

            resultText.setText("Equation");
            confidenceOneText.setText("--%");
            confidenceTwoText.setText("--%");
        });
    }

    // Loads digit.tflite from the assets folder
    private MappedByteBuffer loadModelFile(String modelName) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // Predicts one digit from one drawing box
    private int predictDigit(FingerPaintView box) {

        // Converts the drawing box into a 28x28 bitmap
        Bitmap bitmap = box.exportToBitmap(28, 28);

        // Input array for the model
        float[][][][] input = new float[1][28][28][1];

        // Goes through every pixel in the 28x28 image
        for (int y = 0; y < 28; y++) {
            for (int x = 0; x < 28; x++) {

                // Gets the color of the pixel
                int pixel = bitmap.getPixel(x, y);

                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);

                // Turns color into grayscale
                float gray = (red + green + blue) / 3.0f;

                // Normalizes the value for the model
                input[0][y][x][0] = (255.0f - gray) / 255.0f;
            }
        }

        // Output array has 10 spots, one for each digit 0-9
        float[][] output = new float[1][10];

        // Runs the model
        interpreter.run(input, output);

        int predictedDigit = 0;
        float highestConfidence = output[0][0];

        // Finds the digit with the highest confidence score
        for (int i = 1; i < 10; i++) {
            if (output[0][i] > highestConfidence) {
                highestConfidence = output[0][i];
                predictedDigit = i;
            }
        }

        // Saves confidence for whichever box was checked
        if (box == boxOne) {
            confidenceOne = highestConfidence;
        } else {
            confidenceTwo = highestConfidence;
        }

        return predictedDigit;
    }

    // Predicts both digits, does the math, and displays the answer
    private void calculateResult() {

        // Stops if the model did not load
        if (interpreter == null) {
            resultText.setText("Model not loaded");
            return;
        }

        // Gets the predicted digit from each box
        int predictedOne = predictDigit(boxOne);
        int predictedTwo = predictDigit(boxTwo);

        double answer = 0;

        // Performs the selected operation
        if (operation.equals("+")) {
            answer = predictedOne + predictedTwo;
        } else if (operation.equals("-")) {
            answer = predictedOne - predictedTwo;
        } else if (operation.equals("×")) {
            answer = predictedOne * predictedTwo;
        } else if (operation.equals("÷")) {

            // Prevents dividing by zero
            if (predictedTwo == 0) {
                resultText.setText("Cannot divide by zero");
                return;
            }

            answer = (double) predictedOne / predictedTwo;
        }

        // Displays the equation
        resultText.setText(predictedOne + " " + operation + " " + predictedTwo + " = " + answer);

        // Displays both confidence scores
        confidenceOneText.setText(String.format(Locale.US, "%.1f%%", confidenceOne * 100));
        confidenceTwoText.setText(String.format(Locale.US, "%.1f%%", confidenceTwo * 100));
    }
}
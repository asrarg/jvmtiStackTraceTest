import java.util.Scanner;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Scanner;

final public class Matrix {
	private final int M;             // number of rows
	private final int N;             // number of columns
	private final double[][] data;   // M-by-N array
	// test client
	public static void main (String[] args) {
		long startTime = System.currentTimeMillis();
		if ( args.length != 2 ) {
			System.err.printf("Arguments: <matrix size> <computing threads>");
			System.exit(1);
		}
		final int matrixSize = getIntArg(args[0], 10, 10000, "Invalid matrix size %d, must be between 10 and 10000");
		final int cthreads = getIntArg(args[1], 1, 200, "Invalid computing threads %d, must be between 1 and 200");
		//creating the Matrix
		Thread threads[] = new Thread[cthreads];
		for(int i=0; i<cthreads; i++) {
			threads[i] = new Thread(new Runnable(){
				public void run() {
					for (int xx=0 ; xx< 100; xx++)
					{
						//creating random matrixes and performing operations on them
						Matrix A = Matrix.random(matrixSize, matrixSize );
						Matrix B = Matrix.random(matrixSize, matrixSize );
						A.plus(B).show();
						B.times(A).show();
						A.show();
						//creating 2 other matrixes for Gaussian Elimination
						Matrix a = Matrix.random(5, 5);
						Matrix b = Matrix.random(5, 1);
						Matrix x = a.solve(b);
						x.show();
						
						Matrix c = Matrix.random(5, 5);
						Matrix d = Matrix.random(5, 1);
						Matrix z = c.solve(d);
						z.show();
					}
				}
			});
			threads[i].setName("Gauss");
		}
		for(Thread t: threads) {
			t.start();
		}
		long stopTime = System.currentTimeMillis();
		System.out.println("############################### MATRIX: Elapsed time was " + (stopTime - startTime) + " miliseconds. ###############################");
	}

	// create M-by-N matrix of 0's
	public Matrix(int N) {
		this(N, N);
	}
	// create M-by-N matrix of 0's
	public Matrix(int M, int N) {
		this.M = M;
		this.N = N;
		data = new double[M][N];
	}
	// create matrix based on 2d array
	public Matrix(double[][] data) {
		M = data.length;
		N = data[0].length;
		this.data = new double[M][N];
		for (int i = 0; i < M; i++)
			for (int j = 0; j < N; j++)
				this.data[i][j] = data[i][j];
	}
	// copy constructor
	private Matrix(Matrix A)
	{ 
		this(A.data);
	}
	// create and return a random M-by-N matrix with values between 0 and 1
	public static Matrix random(int M, int N) {
		Matrix A = new Matrix(M, N);
		for (int i = 0; i < M; i++)
			for (int j = 0; j < N; j++)
				A.data[i][j] = Math.random();
		return A;
	}
	// create and return the N-by-N identity matrix
	public static Matrix identity(int N) {
		Matrix I = new Matrix(N, N);
		for (int i = 0; i < N; i++)
			I.data[i][i] = 1;
		return I;
	}
	// swap rows i and j
	private void swap(int i, int j) {
		double[] temp = data[i];
		data[i] = data[j];
		data[j] = temp;
	}
	// create and return the transpose of the invoking matrix
	public Matrix transpose() {
		Matrix A = new Matrix(N, M);
		for (int i = 0; i < M; i++)
			for (int j = 0; j < N; j++)
				A.data[j][i] = this.data[i][j];
		return A;
	}
	// return C = A + B
	public Matrix plus(Matrix B) {
		Matrix A = this;
		if (B.M != A.M || B.N != A.N) throw new RuntimeException("Illegal matrix dimensions.1");
		Matrix C = new Matrix(M, N);
		for (int i = 0; i < M; i++)
			for (int j = 0; j < N; j++)
				C.data[i][j] = A.data[i][j] + B.data[i][j];
		return C;
	}
	// return C = A - B
	public Matrix minus(Matrix B) {
		Matrix A = this;
		if (B.M != A.M || B.N != A.N) throw new RuntimeException("Illegal matrix dimensions.2");
		Matrix C = new Matrix(M, N);
		for (int i = 0; i < M; i++)
			for (int j = 0; j < N; j++)
				C.data[i][j] = A.data[i][j] - B.data[i][j];
		return C;
	}

	// does A = B exactly?
	public boolean eq(Matrix B) {
		Matrix A = this;
		if (B.M != A.M || B.N != A.N) throw new RuntimeException("Illegal matrix dimensions.3");
		for (int i = 0; i < M; i++)
			for (int j = 0; j < N; j++)
				if (A.data[i][j] != B.data[i][j]) return false;
		return true;
	}
	// return C = A * B
	public Matrix times(Matrix B) {
		Matrix A = this;
		if (A.N != B.M) throw new RuntimeException("Illegal matrix dimensions.4");
		Matrix C = new Matrix(A.M, B.N);
		for (int i = 0; i < C.M; i++)
			for (int j = 0; j < C.N; j++)
				for (int k = 0; k < A.N; k++)
					C.data[i][j] += (A.data[i][k] * B.data[k][j]);
		return C;
	}
	// return x = A^-1 b, assuming A is square and has full rank
	public Matrix solve(Matrix rhs) {
		if (M != N || rhs.M != N || rhs.N != 1)
			throw new RuntimeException("Illegal matrix dimensions.5");
		// create copies of the data
		Matrix A = new Matrix(this);
		Matrix b = new Matrix(rhs);
		// Gaussian elimination with partial pivoting
		for (int i = 0; i < N; i++) {
			// find pivot row and swap
			int max = i;
			for (int j = i + 1; j < N; j++)
				if (Math.abs(A.data[j][i]) > Math.abs(A.data[max][i]))
					max = j;
			A.swap(i, max);
			b.swap(i, max);
			// singular
			if (A.data[i][i] == 0.0) throw new RuntimeException("Matrix is singular.");
			// pivot within b
			for (int j = i + 1; j < N; j++)
				b.data[j][0] -= b.data[i][0] * A.data[j][i] / A.data[i][i];
			// pivot within A
			for (int j = i + 1; j < N; j++) {
				double m = A.data[j][i] / A.data[i][i];
				for (int k = i+1; k < N; k++) {
					A.data[j][k] -= A.data[i][k] * m;
				}
				A.data[j][i] = 0.0;
			}
		}
		// back substitution
		Matrix x = new Matrix(N, 1);
		for (int j = N - 1; j >= 0; j--) {
			double t = 0.0;
			for (int k = j + 1; k < N; k++)
				t += A.data[j][k] * x.data[k][0];
			x.data[j][0] = (b.data[j][0] - t) / A.data[j][j];
		}
		return x;
	}
	//#############################################################################################################################
	// print matrix to standard output
	public void show() {
		Writer writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("theMatrix.txt"), "utf-8"));
			String toPrint = "";
			for (int i = 0; i < M; i++) {
				for (int j = 0; j < N; j++) {
					toPrint = String.format("%9.4f ", data[i][j]);
					writer.write(toPrint);			
					//System.out.printf("%9.4f ", data[i][j]);
				}
				writer.write("\n");
			}
		} catch (IOException ex) {
			System.out.println("Error while writting in file..");
		} finally {
			try {writer.close();} catch (Exception ex) {/*ignore*/}
		}
	}
//#############################################################################################################################
	private static int getIntArg(String arg, int min, int max, String mesg) {
		try {
			int result = Integer.parseInt(arg);
			if ( result < min || result > max ) {
				System.err.printf(mesg, result);
				System.exit(1);
			}
			return result;
		}
		catch ( NumberFormatException ex ) {
			System.err.println(String.format("Invalid integer input %s", arg));
			System.exit(1);
		}
		return -1;
	}
}
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

/******************************************************************************
 *  Compilation:  javac Matrix.java
 *  Execution:    java Matrix
 *
 *  A bare-bones immutable data type for N-by-N matrices.
 *
 ******************************************************************************/

final public class Matrix {
	private final int N;             // number of columns
	private final double[][] data;   // M-by-N array

	// create M-by-N matrix of 0's
	public Matrix(int N) {
		this.N = N;
		data = new double[N][N];
	}

	// create matrix based on 2d array
	public Matrix(double[][] data) {
		//M = data.length;
		N = data[0].length;
		this.data = new double[N][N];
		for (int i = 0; i < N; i++)
			for (int j = 0; j < N; j++)
				this.data[i][j] = data[i][j];
	}

	// copy constructor
	private Matrix(Matrix A) { this(A.data); }

	// create and return a random N-by-N matrix with values between 0 and 1
	public static Matrix random(int N) {
		Matrix A = new Matrix(N);
		for (int i = 0; i < N; i++) {
			double diag = 0;
			for (int j = 0; j < N; j++)
				diag += A.data[i][j] = Math.random();
			A.data[i][i] = diag;
		}
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
		Matrix A = new Matrix(N, N);
		for (int i = 0; i < N; i++)
			for (int j = 0; j < N; j++)
				A.data[j][i] = this.data[i][j];
		return A;
	}

	// return C = A + B
	public Matrix plus(Matrix B) {
		Matrix A = this;
		if (B.N != A.N) throw new RuntimeException("Illegal matrix dimensions.");
		Matrix C = new Matrix(N);
		for (int i = 0; i < N; i++)
			for (int j = 0; j < N; j++)
				C.data[i][j] = A.data[i][j] + B.data[i][j];
		return C;
	}


	// return C = A - B
	public Matrix minus(Matrix B) {
		Matrix A = this;
		if (B.N != A.N) throw new RuntimeException("Illegal matrix dimensions.");
		Matrix C = new Matrix(N);
		for (int i = 0; i < N; i++)
			for (int j = 0; j < N; j++)
				C.data[i][j] = A.data[i][j] - B.data[i][j];
		return C;
	}

	// does A = B exactly?
	public boolean eq(Matrix B) {
		Matrix A = this;
		//if (B.M != A.M || B.N != A.N) throw new RuntimeException("Illegal matrix dimensions.");
		for (int i = 0; i < N; i++)
			for (int j = 0; j < N; j++)
				if (A.data[i][j] != B.data[i][j]) return false;
		return true;
	}

	// return C = A * B
	public Matrix times(Matrix B) {
		Matrix A = this;
		//if (A.N != B.M) throw new RuntimeException("Illegal matrix dimensions.");
		Matrix C = new Matrix(A.N, B.N);
		for (int i = 0; i < C.N; i++)
			for (int j = 0; j < C.N; j++)
				for (int k = 0; k < A.N; k++)
					C.data[i][j] += (A.data[i][k] * B.data[k][j]);
		return C;
	}


	// return x = A^-1 b, assuming A is square and has full rank
	public Matrix solve(Matrix rhs) {
		//if (M != N || rhs.M != N || rhs.N != 1)
			//throw new RuntimeException("Illegal matrix dimensions.");

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

	// print matrix to standard output
	public void show()
	{
		for (int i = 0; i < N; i++)
		{
			for (int j = 0; j < N; j++) 
				StdOut.printf("%9.4f ", data[i][j]);
			StdOut.println();
		}
	}



	private static int getIntArg(String arg, int min, int max, String mesg) {
		try {
			int result = Integer.parseInt(arg);
			if ( result < min || result > max ) {
				System.err.println(mesg);
				System.exit(1);
			}
			return result;
		}
		catch ( NumberFormatException ex ) {
			System.err.println(String.format("Invalid integer input %s", arg));
			System.exit(1);
		}
	}
	
	// test client
	public static void main (String[] args) {
		if ( args.length != 4 ) {
			System.err.printf("Arguments: <matrix size> <computing threads> <monitored threads> <trace interval>");
			System.exit(1);
		}
		
		int matrix = getIntArg(args[0], 10, 10000, "Invalid matrix size, must be between 10 and 10000");
		int cthreads = getIntArg(args[1], 1, 200, "Invalid computing threads, must be between 1 and 200");
		int tthreads = getIntArg(args[2], 1, cthreads, "Invalid monitored threads, must be between 1 and total number of threads");
		int traceint = getIntArg(args[3], 1, 1000, "Invalid trace interval, must be between 1 and 1000 ms");
		
		//creating the Matrix
		Thread threads[] = new Thread[cthreads];
		for(int i=0; i<cthreads; i++) {
			threads[i] = new Thread(new Runnable(){
				public void run() {
					Matrix A = Matrix.random(matrix, matrix );
					A.show();
				}
			});
		}
		List threadList = Arrays.asList(threads);
		Collections.shuffle(threadList);
		threadList.toArray(threads);
		Thread traced[] = Arrays.copyOf(original, tthreads);

		for(Thread t: threads) {
			t.start();
		}
		
		StdOut.println();
		
		
		
		/*
		double[][] d = { { 1, 2, 3 }, { 4, 5, 6 }, { 9, 1, 3} };
		Matrix D = new Matrix(d);
		D.show();        
		StdOut.println();

		Matrix A = Matrix.random(5, 5);
		A.show(); 
		StdOut.println();

		A.swap(1, 2);
		A.show(); 
		StdOut.println();

		Matrix B = A.transpose();
		B.show(); 
		StdOut.println();

		Matrix C = Matrix.identity(5);
		C.show(); 
		StdOut.println();

		A.plus(B).show();
		StdOut.println();

		B.times(A).show();
		StdOut.println();

		// shouldn't be equal since AB != BA in general    
				StdOut.println(A.times(B).eq(B.times(A)));
		StdOut.println();

		Matrix b = Matrix.random(5, 1);
		b.show();
		StdOut.println();

		Matrix x = A.solve(b);
		x.show();
		StdOut.println();

		A.times(x).show();
		*/

	}
}
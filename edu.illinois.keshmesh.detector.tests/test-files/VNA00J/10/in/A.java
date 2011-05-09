/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package p;

import edu.illinois.keshmesh.annotations.EntryPoint;

/**
 * 
 * This test demonstrates the imprecision of 1 object context sensitivity.
 * 
 */
public class A {

	static D f;

	@EntryPoint
	public static void main(String[] args) {
		B b1 = new B();
		synchronized (new Object()) {
			f = new ThreadSafeD();
			b1.setD(f);
		}
		B b2 = new B();
		D d = new D();
		b2.setD(d);

		b2.d.value = 0;
	}

}

class B {

	public D d;

	void setD(D d) {
		C.instance.setD(this, d);
	}

}

class C {

	public static C instance = new C();

	void setD(B b, D d) {
		b.d = d;
	}

}

class D {
	int value;
	
}

class ThreadSafeD extends D implements Runnable {

	public void run() {
		
	}

}
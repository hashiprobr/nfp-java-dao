package br.pro.hashi.nfp.dao.exception;

public class BytecodeFirestoreException extends FirestoreException {
	private static final long serialVersionUID = -5450838840042174492L;

	public BytecodeFirestoreException(Exception exception) {
		super("Invalid Firestore bytecode", exception);
	}
}

package br.pro.hashi.nfp.dao.exception;

import java.io.IOException;

public class IOFirebaseException extends FirebaseException {
	private static final long serialVersionUID = -2574178978649087112L;

	public IOFirebaseException(IOException exception) {
		super(exception);
	}
}

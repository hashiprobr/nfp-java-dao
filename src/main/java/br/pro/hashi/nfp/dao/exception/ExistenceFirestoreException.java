package br.pro.hashi.nfp.dao.exception;

public class ExistenceFirestoreException extends InputFirestoreException {
	private static final long serialVersionUID = -2989842294577345185L;

	public ExistenceFirestoreException(String message) {
		super(message);
	}
}

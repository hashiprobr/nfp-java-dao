package br.pro.hashi.nfp.dao.exception;

public class QueryFirestoreException extends FirestoreException {
	private static final long serialVersionUID = 602180335792702194L;

	public QueryFirestoreException(String message) {
		super("Query execution failed", message);
	}
}

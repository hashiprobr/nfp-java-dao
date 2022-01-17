package br.pro.hashi.nfp.dao;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.google.cloud.WriteChannel;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.Query.Direction;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;

import br.pro.hashi.nfp.dao.exception.ExecutionFirestoreException;
import br.pro.hashi.nfp.dao.exception.ExistenceFirestoreException;
import br.pro.hashi.nfp.dao.exception.ExistenceStorageException;
import br.pro.hashi.nfp.dao.exception.FormatFirestoreException;
import br.pro.hashi.nfp.dao.exception.FormatStorageException;
import br.pro.hashi.nfp.dao.exception.IOFirebaseException;
import br.pro.hashi.nfp.dao.exception.InterruptedFirestoreException;

public abstract class FirebaseDAO<T extends FirebaseObject> {
	public class Selection {
		private final Query query;
		private String orderBy;
		private boolean descending;
		private int offset;
		private int limit;

		private Selection(Query query) {
			this.query = query;
			this.orderBy = null;
			this.descending = false;
			this.offset = 0;
			this.limit = 0;
		}

		public Selection orderBy(String orderBy) {
			if (orderBy == null) {
				throw new FormatFirestoreException("Order key cannot be null");
			}
			if (orderBy.isBlank()) {
				throw new FormatFirestoreException("Order key cannot be blank");
			}
			this.orderBy = orderBy;
			return this;
		}

		public Selection descending() {
			this.descending = true;
			return null;
		}

		public Selection offset(int offset) {
			if (offset < 1) {
				throw new FormatFirestoreException("Offset must be positive");
			}
			this.offset = offset;
			return null;
		}

		public Selection limit(int limit) {
			if (limit < 1) {
				throw new FormatFirestoreException("Limit must be positive");
			}
			this.limit = limit;
			return null;
		}

		private QuerySnapshot getDocuments() {
			Query query = this.query;
			if (orderBy != null) {
				if (descending) {
					query = query.orderBy(orderBy, Direction.DESCENDING);
				} else {
					query = query.orderBy(orderBy);
				}
			}
			if (offset > 0) {
				query = query.offset(offset);
			}
			if (limit > 0) {
				query = query.limit(limit);
			}
			QuerySnapshot documents;
			try {
				documents = query.get().get();
			} catch (ExecutionException exception) {
				throw new ExecutionFirestoreException(exception);
			} catch (InterruptedException exception) {
				throw new InterruptedFirestoreException(exception);
			}
			return documents;
		}
	}

	private final Class<T> type;
	private final String path;
	private final boolean autokey;
	private Firestore firestore;
	private Bucket bucket;
	private CollectionReference collection;

	@SuppressWarnings("unchecked")
	protected FirebaseDAO(String path) {
		ParameterizedType genericType = (ParameterizedType) getClass().getGenericSuperclass();
		Type[] types = genericType.getActualTypeArguments();
		this.type = (Class<T>) types[0];

		try {
			type.getConstructor();
		} catch (NoSuchMethodException exception) {
			throw new FormatFirestoreException("Class %s must have a public no-argument constructor".formatted(type.getName()));
		}

		if (path == null) {
			throw new FormatFirestoreException("Path cannot be null");
		}
		if (path.isBlank()) {
			throw new FormatFirestoreException("Path cannot be blank");
		}
		if (path.indexOf('/') != -1) {
			throw new FormatFirestoreException("Path cannot have slashes");
		}
		this.path = path;

		this.autokey = AutokeyFirebaseObject.class.isAssignableFrom(this.type);
	}

	private void checkRead(String key) {
		if (key == null) {
			throw new FormatFirestoreException("Key cannot be null");
		}
		if (key.isBlank()) {
			throw new FormatFirestoreException("Key cannot be blank");
		}
		if (key.indexOf('/') != -1) {
			throw new FormatFirestoreException("Key cannot have slashes");
		}
	}

	private void checkRead(List<String> keys) {
		if (keys == null) {
			throw new FormatFirestoreException("List of keys cannot be null");
		}
		if (keys.isEmpty()) {
			throw new FormatFirestoreException("List of keys cannot be empty");
		}
		for (String key : keys) {
			checkRead(key);
		}
	}

	private void checkWrite(T value) {
		if (value == null) {
			throw new FormatFirestoreException("Value cannot be null");
		}
	}

	private void checkWrite(List<T> values) {
		if (values == null) {
			throw new FormatFirestoreException("List of values cannot be null");
		}
		if (values.isEmpty()) {
			throw new FormatFirestoreException("List of values cannot be empty");
		}
		for (T value : values) {
			checkWrite(value);
		}
	}

	private void checkIn(List<?> values) {
		if (values == null) {
			throw new FormatFirestoreException("List of values cannot be null");
		}
		if (values.isEmpty()) {
			throw new FormatFirestoreException("List of values cannot be empty");
		}
	}

	private void checkQuery(Selection selection) {
		if (selection == null) {
			throw new FormatFirestoreException("Selection cannot be null");
		}
	}

	private void checkFile(InputStream stream) {
		if (stream == null) {
			throw new FormatStorageException("Stream cannot be null");
		}
	}

	private String buildPath(String key, String name) {
		checkRead(key);
		if (name == null) {
			throw new FormatStorageException("Name cannot be null");
		}
		if (name.isBlank()) {
			throw new FormatStorageException("Name cannot be blank");
		}
		if (name.indexOf('/') != -1) {
			throw new FormatStorageException("Name cannot have slashes");
		}
		return "%s/%s/%s".formatted(path, key, name);
	}

	@SuppressWarnings("unchecked")
	public <S extends FirebaseDAO<T>> S to(String name) {
		Firebase firebase;
		if (name == null) {
			firebase = Firebase.getInstance();
		} else {
			firebase = Firebase.getInstance(name);
		}
		this.firestore = firebase.getFirestore();
		this.bucket = firebase.getBucket();
		this.collection = this.firestore.collection(this.path);
		return (S) this;
	}

	@SuppressWarnings("unchecked")
	public <S extends FirebaseDAO<T>> S init() {
		if (firestore == null) {
			return to(null);
		} else {
			return (S) this;
		}
	}

	public Selection select() {
		init();
		return new Selection(collection);
	}

	public Selection select(List<String> keys) {
		checkRead(keys);
		init();
		return new Selection(collection.whereIn(FieldPath.documentId(), keys));
	}

	public Selection selectExcept(List<String> keys) {
		checkRead(keys);
		init();
		return new Selection(collection.whereNotIn(FieldPath.documentId(), keys));
	}

	public Selection selectWhereIn(String key, List<Object> values) {
		checkRead(key);
		checkIn(values);
		init();
		return new Selection(collection.whereIn(key, values));
	}

	public Selection selectWhereNotIn(String key, List<Object> values) {
		checkRead(key);
		checkIn(values);
		init();
		return new Selection(collection.whereNotIn(key, values));
	}

	public Selection selectWhereEqualTo(String key, Object value) {
		checkRead(key);
		init();
		return new Selection(collection.whereEqualTo(key, value));
	}

	public Selection selectWhereNotEqualTo(String key, Object value) {
		checkRead(key);
		init();
		return new Selection(collection.whereNotEqualTo(key, value));
	}

	public Selection selectWhereLessThan(String key, Object value) {
		checkRead(key);
		init();
		return new Selection(collection.whereLessThan(key, value));
	}

	public Selection selectWhereLessThanOrEqualTo(String key, Object value) {
		checkRead(key);
		init();
		return new Selection(collection.whereLessThanOrEqualTo(key, value));
	}

	public Selection selectWhereGreaterThan(String key, Object value) {
		checkRead(key);
		init();
		return new Selection(collection.whereGreaterThan(key, value));
	}

	public Selection selectWhereGreaterThanOrEqualTo(String key, Object value) {
		checkRead(key);
		init();
		return new Selection(collection.whereGreaterThanOrEqualTo(key, value));
	}

	public Selection selectWhereContains(String key, Object value) {
		checkRead(key);
		init();
		return new Selection(collection.whereArrayContains(key, value));
	}

	public Selection selectWhereContainsAny(String key, List<?> values) {
		checkRead(key);
		checkIn(values);
		init();
		return new Selection(collection.whereArrayContainsAny(key, values));
	}

	public boolean exists(String key) {
		checkRead(key);
		init();
		DocumentSnapshot document;
		try {
			document = collection.document(key).get().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		return document.exists();
	}

	public String create(T value) {
		checkWrite(value);
		init();
		String key;
		try {
			DocumentReference document;
			if (autokey) {
				if (value.getKey() != null) {
					throw new FormatFirestoreException("Key must be null");
				}
				document = collection.document();
				key = document.getId();
				((AutokeyFirebaseObject) value).setKey(key);
			} else {
				key = value.getKey();
				checkRead(key);
				document = collection.document(key);
				if (document.get().get().exists()) {
					throw new ExistenceFirestoreException("Key %s already exists".formatted(key));
				}
			}
			document.set(value).get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		return key;
	}

	public List<String> create(List<T> values) {
		checkWrite(values);
		init();
		String key;
		DocumentReference document;
		WriteBatch batch = firestore.batch();
		List<String> keys = new ArrayList<>();
		if (autokey) {
			for (T value : values) {
				if (value.getKey() != null) {
					throw new FormatFirestoreException("All keys must be null");
				}
				document = collection.document();
				key = document.getId();
				((AutokeyFirebaseObject) value).setKey(key);
				batch.set(document, value);
				keys.add(key);
			}
		} else {
			for (T value : values) {
				key = value.getKey();
				checkRead(key);
				document = collection.document(key);
				batch.set(document, value);
				keys.add(key);
			}
			Query query = collection.whereIn(FieldPath.documentId(), keys);
			QuerySnapshot documents;
			try {
				documents = query.get().get();
			} catch (ExecutionException exception) {
				throw new ExecutionFirestoreException(exception);
			} catch (InterruptedException exception) {
				throw new InterruptedFirestoreException(exception);
			}
			if (documents.size() > 0) {
				throw new ExistenceFirestoreException("Some keys already exist");
			}
		}
		try {
			batch.commit().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		return keys;
	}

	public String create(String key, String name, InputStream stream) {
		checkFile(stream);
		String blobPath = buildPath(key, name);
		init();
		if (bucket.get(blobPath) != null) {
			throw new ExistenceStorageException("Path %s already exists".formatted(blobPath));
		}
		Blob blob = bucket.create(blobPath, stream);
		blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));
		return blob.getMediaLink();
	}

	public T retrieve(String key, boolean error) {
		checkRead(key);
		init();
		DocumentSnapshot document;
		try {
			document = collection.document(key).get().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		if (!document.exists()) {
			if (error) {
				throw new ExistenceFirestoreException("Key %s does not exist".formatted(key));
			} else {
				return null;
			}
		}
		return document.toObject(type);
	}

	public T retrieve(String key) {
		return retrieve(key, true);
	}

	public List<T> retrieve(Selection selection) {
		checkQuery(selection);
		List<T> values = new ArrayList<>();
		for (DocumentSnapshot document : selection.getDocuments()) {
			values.add(document.toObject(type));
		}
		return values;
	}

	public String retrieve(String key, String name, boolean error) {
		String blobPath = buildPath(key, name);
		init();
		Blob blob = bucket.get(blobPath);
		if (blob == null) {
			if (error) {
				throw new ExistenceStorageException("Path %s does not exist".formatted(blobPath));
			} else {
				return null;
			}
		}
		return blob.getMediaLink();
	}

	public String retrieve(String key, String name) {
		return retrieve(key, name, true);
	}

	public void update(T value) {
		checkWrite(value);
		String key = value.getKey();
		checkRead(key);
		init();
		DocumentReference document = collection.document(key);
		try {
			if (!document.get().get().exists()) {
				throw new ExistenceFirestoreException("Key %s does not exist".formatted(key));
			}
			document.set(value).get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
	}

	public void update(List<T> values) {
		checkWrite(values);
		init();
		WriteBatch batch = firestore.batch();
		List<String> keys = new ArrayList<>();
		for (T value : values) {
			String key = value.getKey();
			checkRead(key);
			batch.set(collection.document(key), value);
			keys.add(key);
		}
		Query query = collection.whereIn(FieldPath.documentId(), keys);
		QuerySnapshot documents;
		try {
			documents = query.get().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		if (documents.size() < values.size()) {
			throw new ExistenceFirestoreException("Some keys do not exist");
		}
		try {
			batch.commit().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
	}

	public String update(String key, String name, InputStream stream) {
		checkFile(stream);
		String blobPath = buildPath(key, name);
		init();
		Blob blob = bucket.get(blobPath);
		if (blob == null) {
			throw new ExistenceStorageException("Path %s does not exist".formatted(blobPath));
		}
		WriteChannel writer = blob.writer();
		try {
			ByteBuffer src = ByteBuffer.wrap(stream.readAllBytes());
			writer.write(src);
			writer.close();
		} catch (IOException exception) {
			throw new IOFirebaseException(exception);
		}
		blob = bucket.get(blobPath);
		return blob.getMediaLink();
	}

	public void delete(String key, boolean error) {
		checkRead(key);
		init();
		DocumentReference document = collection.document(key);
		try {
			if (!document.get().get().exists()) {
				if (error) {
					throw new ExistenceFirestoreException("Key %s does not exist".formatted(key));
				} else {
					return;
				}
			}
			document.delete().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
	}

	public void delete(String key) {
		delete(key, true);
	}

	public void delete(Selection selection) {
		checkQuery(selection);
		WriteBatch batch = firestore.batch();
		for (DocumentSnapshot document : selection.getDocuments()) {
			batch.delete(document.getReference());
		}
		try {
			batch.commit().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
	}

	public void delete(String key, String name, boolean error) {
		String blobPath = buildPath(key, name);
		init();
		Blob blob = bucket.get(blobPath);
		if (blob == null) {
			if (error) {
				throw new ExistenceStorageException("Path %s does not exist".formatted(blobPath));
			} else {
				return;
			}
		}
		blob.delete();
	}

	public void delete(String key, String name) {
		delete(key, name, true);
	}
}

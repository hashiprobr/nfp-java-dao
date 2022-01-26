package br.pro.hashi.nfp.dao;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
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
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;

import br.pro.hashi.nfp.dao.exception.AccessFirestoreException;
import br.pro.hashi.nfp.dao.exception.ExecutionFirestoreException;
import br.pro.hashi.nfp.dao.exception.ExistenceFirestoreException;
import br.pro.hashi.nfp.dao.exception.ExistenceStorageException;
import br.pro.hashi.nfp.dao.exception.FormatFirestoreException;
import br.pro.hashi.nfp.dao.exception.FormatStorageException;
import br.pro.hashi.nfp.dao.exception.IOFirebaseException;
import br.pro.hashi.nfp.dao.exception.InterruptedFirestoreException;

public abstract class DAO<T> {
	private final String path;
	private final Class<T> type;
	private Field field;
	private boolean auto;
	private Firebase firebase;
	private Firestore firestore;
	private CollectionReference collection;
	private Bucket bucket;

	@SuppressWarnings("unchecked")
	protected DAO(String path) {
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

		ParameterizedType genericType = (ParameterizedType) getClass().getGenericSuperclass();
		Type[] types = genericType.getActualTypeArguments();
		this.type = (Class<T>) types[0];

		String className = this.type.getName();

		try {
			this.type.getConstructor();
		} catch (NoSuchMethodException exception) {
			throw new FormatFirestoreException("Class %s must have a public no-argument constructor".formatted(className));
		}

		this.field = null;
		this.auto = false;
		for (Field field : this.type.getDeclaredFields()) {
			String fieldName = field.getName();
			if (field.isAnnotationPresent(Key.class)) {
				if (field.isAnnotationPresent(Autokey.class)) {
					throw new FormatFirestoreException("Field %s of class %s cannot be both a key and an autokey".formatted(fieldName, className));
				} else {
					if (this.field == null) {
						this.field = field;
					} else {
						if (this.auto) {
							throw new FormatFirestoreException("Class %s cannot have both an autokey and a key".formatted(className));
						} else {
							throw new FormatFirestoreException("Class %s cannot have more than one key".formatted(className));
						}
					}
				}
			} else {
				if (field.isAnnotationPresent(Autokey.class)) {
					if (this.field == null) {
						if (field.getType() != String.class) {
							throw new FormatFirestoreException("Autokey %s of class %s must be a string".formatted(fieldName, className));
						}
						this.field = field;
						this.auto = true;
					} else {
						if (this.auto) {
							throw new FormatFirestoreException("Class %s cannot have more than one autokey".formatted(className));
						} else {
							throw new FormatFirestoreException("Class %s cannot have both a key and an autokey".formatted(className));
						}
					}
				}
			}
		}

		if (this.field == null) {
			throw new FormatFirestoreException("Class %s must have either a key or an autokey".formatted(className));
		}
		this.field.setAccessible(true);

		this.firebase = null;
		this.firestore = null;
		this.collection = null;
		this.bucket = null;
	}

	private String get(T value) {
		Object key;
		try {
			key = field.get(value);
		} catch (IllegalAccessException exception) {
			throw new AccessFirestoreException(exception);
		}
		if (key == null) {
			return null;
		} else {
			return key.toString();
		}
	}

	private void set(T value, String key) {
		try {
			field.set(value, key);
		} catch (IllegalAccessException exception) {
			throw new AccessFirestoreException(exception);
		}
	}

	private void refresh() {
		Firestore instance = firebase.getFirestore();
		if (firestore != instance) {
			firestore = instance;
			collection = firestore.collection(path);
		}
		bucket = firebase.getBucket();
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
	public <S extends DAO<T>> S using(FirebaseManager manager, String name) {
		if (name == null) {
			firebase = manager.get();
		} else {
			firebase = manager.get(name);
		}
		firebase.checkConnection();
		refresh();
		return (S) this;
	}

	public <S extends DAO<T>> S using(FirebaseManager manager) {
		return using(manager, null);
	}

	public <S extends DAO<T>> S using(String name) {
		return using(Firebase.Manager(), name);
	}

	@SuppressWarnings("unchecked")
	public <S extends DAO<T>> S initialized() {
		if (firebase == null) {
			return using(Firebase.Manager(), null);
		} else {
			firebase.checkExistence();
			firebase.checkConnection();
			refresh();
			return (S) this;
		}
	}

	public Selection select() {
		initialized();
		return new Selection(collection);
	}

	public Selection select(List<String> keys) {
		checkRead(keys);
		initialized();
		return new Selection(collection.whereIn(FieldPath.documentId(), keys));
	}

	public Selection selectExcept(List<String> keys) {
		checkRead(keys);
		initialized();
		return new Selection(collection.whereNotIn(FieldPath.documentId(), keys));
	}

	public Selection selectWhereIn(String key, List<Object> values) {
		checkRead(key);
		checkIn(values);
		initialized();
		return new Selection(collection.whereIn(key, values));
	}

	public Selection selectWhereNotIn(String key, List<Object> values) {
		checkRead(key);
		checkIn(values);
		initialized();
		return new Selection(collection.whereNotIn(key, values));
	}

	public Selection selectWhereEqualTo(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereEqualTo(key, value));
	}

	public Selection selectWhereNotEqualTo(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereNotEqualTo(key, value));
	}

	public Selection selectWhereLessThan(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereLessThan(key, value));
	}

	public Selection selectWhereLessThanOrEqualTo(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereLessThanOrEqualTo(key, value));
	}

	public Selection selectWhereGreaterThan(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereGreaterThan(key, value));
	}

	public Selection selectWhereGreaterThanOrEqualTo(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereGreaterThanOrEqualTo(key, value));
	}

	public Selection selectWhereContains(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereArrayContains(key, value));
	}

	public Selection selectWhereContainsAny(String key, List<?> values) {
		checkRead(key);
		checkIn(values);
		initialized();
		return new Selection(collection.whereArrayContainsAny(key, values));
	}

	public boolean exists(String key) {
		checkRead(key);
		initialized();
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
		initialized();
		String key;
		try {
			DocumentReference document;
			if (auto) {
				if (get(value) != null) {
					throw new FormatFirestoreException("Key must be null");
				}
				document = collection.document();
				key = document.getId();
				set(value, key);
			} else {
				key = get(value);
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
		initialized();
		String key;
		DocumentReference document;
		WriteBatch batch = firestore.batch();
		List<String> keys = new ArrayList<>();
		if (auto) {
			for (T value : values) {
				if (get(value) != null) {
					throw new FormatFirestoreException("All keys must be null");
				}
				document = collection.document();
				key = document.getId();
				set(value, key);
				batch.set(document, value);
				keys.add(key);
			}
		} else {
			for (T value : values) {
				key = get(value);
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
		initialized();
		if (bucket.get(blobPath) != null) {
			throw new ExistenceStorageException("Path %s already exists".formatted(blobPath));
		}
		Blob blob = bucket.create(blobPath, stream);
		blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));
		return blob.getMediaLink();
	}

	public T retrieve(String key, boolean error) {
		checkRead(key);
		initialized();
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
		initialized();
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
		String key = get(value);
		checkRead(key);
		initialized();
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
		initialized();
		WriteBatch batch = firestore.batch();
		List<String> keys = new ArrayList<>();
		for (T value : values) {
			String key = get(value);
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
		initialized();
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
		initialized();
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
		initialized();
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

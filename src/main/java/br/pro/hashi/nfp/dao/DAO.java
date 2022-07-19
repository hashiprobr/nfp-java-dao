package br.pro.hashi.nfp.dao;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.cloud.WriteChannel;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;

import br.pro.hashi.nfp.dao.exception.AccessFirestoreException;
import br.pro.hashi.nfp.dao.exception.BytecodeFirestoreException;
import br.pro.hashi.nfp.dao.exception.ExecutionFirestoreException;
import br.pro.hashi.nfp.dao.exception.InterruptedFirestoreException;
import br.pro.hashi.nfp.dao.exception.QueryFirestoreException;
import br.pro.hashi.nfp.dao.exception.RequestFirestoreException;
import br.pro.hashi.nfp.dao.exception.StorageFirestoreException;

public abstract class DAO<T> {
	private static final int CODE_LIMIT = 1500;
	private static final String CODE_INVALID = "__.*__";

	private final String path;
	private final Class<T> type;
	private Firebase firebase;
	private Firestore firestore;
	private CollectionReference collection;
	private Bucket bucket;
	private Source source;
	private boolean auto;
	private Field keyField;
	private Map<String, Field> fileFields;

	@SuppressWarnings("unchecked")
	protected DAO(String path) {
		if (path == null) {
			throw new IllegalArgumentException("Firestore code cannot be null");
		}
		this.path = clean(path);

		Class<?> type = getClass();
		Class<?> ancestor = type.getSuperclass();
		while (!ancestor.equals(DAO.class)) {
			type = ancestor;
			ancestor = type.getSuperclass();
		}
		ParameterizedType genericType = (ParameterizedType) type.getGenericSuperclass();
		Type[] types = genericType.getActualTypeArguments();
		this.type = (Class<T>) types[0];

		this.firebase = null;
		this.firestore = null;
		this.collection = null;
		this.bucket = null;

		this.source = null;
		this.auto = false;
		this.keyField = null;
		this.fileFields = null;
	}

	private String clean(String code) {
		code = code.strip();
		if (code.isEmpty()) {
			throw new IllegalArgumentException("Firestore code cannot be blank");
		}
		if (code.getBytes().length > CODE_LIMIT) {
			throw new IllegalArgumentException("Firestore code cannot have more than %d bytes".formatted(CODE_LIMIT));
		}
		if (code.indexOf('/') != -1) {
			throw new IllegalArgumentException("Firestore code cannot have slashes");
		}
		if (code.equals(".") || code.equals("..")) {
			throw new IllegalArgumentException("Firestore code cannot be a single point or double points");
		}
		if (code.matches(CODE_INVALID)) {
			throw new IllegalArgumentException("Firestore code cannot match the regular expression %s".formatted(CODE_INVALID));
		}
		return code;
	}

	private String convert(Object rawKey) {
		if (rawKey == null) {
			throw new IllegalArgumentException("Firestore code cannot be null");
		}
		return clean(rawKey.toString());
	}

	private void validate(T object) {
		if (object == null) {
			throw new IllegalArgumentException("Object cannot be null");
		}
		ready();
	}

	private void validate(Selection selection) {
		if (selection == null) {
			throw new IllegalArgumentException("Selection cannot be null");
		}
		ready();
		if (!selection.getFirestore().equals(firestore)) {
			throw new QueryFirestoreException("Firebase instance is not the one that generated the selection");
		}
	}

	private void validate(Map<String, InputStream> streams, String name) {
		if (streams.get(name) == null) {
			throw new IllegalArgumentException("Input stream %s cannot be null".formatted(name));
		}
	}

	private String join(String key, String name) {
		return "%s/%s/%s".formatted(path, key, name);
	}

	private Object get(Field field, T object) {
		Object value;
		try {
			value = field.get(object);
		} catch (IllegalAccessException exception) {
			throw new AccessFirestoreException(exception);
		}
		return value;
	}

	private void set(Field field, T object, String value) {
		try {
			field.set(object, value);
		} catch (IllegalAccessException exception) {
			throw new AccessFirestoreException(exception);
		}
	}

	private DocumentReference preUpdate(String key) {
		DocumentReference document = collection.document(key);
		try {
			if (!document.get().get().exists()) {
				throw new RequestFirestoreException("Key %s does not exist in database".formatted(key));
			}
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		return document;
	}

	private String createOrUpdate(Map<String, InputStream> streams, String name, String key) {
		InputStream stream = streams.get(name);
		String blobPath = join(key, name);
		Blob blob = bucket.get(blobPath);
		if (blob == null) {
			blob = bucket.create(blobPath, stream);
			blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));
		} else {
			WriteChannel writer = blob.writer();
			try {
				ByteBuffer source = ByteBuffer.wrap(stream.readAllBytes());
				writer.write(source);
				writer.close();
			} catch (IOException exception) {
				throw new StorageFirestoreException(exception);
			}
			blob = bucket.get(blobPath);
		}
		return blob.getMediaLink();
	}

	private void createOrUpdate(T object, Map<String, InputStream> streams, String key) {
		Field field;
		for (String name : streams.keySet()) {
			field = fileFields.get(name);
			if (field == null) {
				throw new IllegalArgumentException("File field %s does not exist in class %s".formatted(name, type.getName()));
			}
			if (get(field, object) != null) {
				throw new IllegalArgumentException("File field %s must be null in object".formatted(name));
			}
			validate(streams, name);
		}
		for (String name : streams.keySet()) {
			field = fileFields.get(name);
			String url = createOrUpdate(streams, name, key);
			set(field, object, url);
		}
	}

	private void delete(List<String> blobPaths) {
		if (!blobPaths.isEmpty()) {
			for (Blob blob : bucket.get(blobPaths)) {
				if (blob != null) {
					blob.delete();
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private T postRetrieve(DocumentSnapshot document, Class<?> proxyType) {
		T object;
		if (proxyType == null) {
			object = document.toObject(type);
		} else {
			Object proxy = document.toObject(proxyType);
			try {
				Field field = proxyType.getField("that");
				field.setAccessible(true);
				object = (T) field.get(proxy);
			} catch (NoSuchFieldException exception) {
				throw new BytecodeFirestoreException(exception);
			} catch (IllegalAccessException exception) {
				throw new BytecodeFirestoreException(exception);
			}
		}
		return object;
	}

	private void postCreateOrUpdate(T object, DocumentReference document, Class<? extends Adapter<T>> adapter) {
		Object proxy = null;
		if (adapter != null) {
			Class<?> proxyType = source.compile(adapter.getName());
			try {
				proxy = proxyType.getDeclaredConstructor(type).newInstance(object);
			} catch (NoSuchMethodException exception) {
				throw new BytecodeFirestoreException(exception);
			} catch (InvocationTargetException exception) {
				throw new BytecodeFirestoreException(exception);
			} catch (IllegalAccessException exception) {
				throw new BytecodeFirestoreException(exception);
			} catch (InstantiationException exception) {
				throw new BytecodeFirestoreException(exception);
			}
		}
		try {
			if (proxy == null) {
				document.set(object).get();
			} else {
				document.set(proxy).get();
			}
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
	}

	@SuppressWarnings("unchecked")
	private <S extends DAO<T>> S refreshed() {
		firebase.connect();
		firestore = firebase.getFirestore();
		collection = firebase.collection(path);
		bucket = firebase.getBucket();
		if (source == null) {
			source = firebase.reflect(type);
			auto = source.isAuto();
			keyField = source.getKeyField();
			fileFields = source.getFileFields();
		}
		return (S) this;
	}

	public <S extends DAO<T>> S fromCredentials(FirebaseManager manager, String path) {
		firebase = manager.getFromCredentials(path);
		return refreshed();
	}

	public <S extends DAO<T>> S fromCredentials(String path) {
		return fromCredentials(Firebase.manager(), path);
	}

	public <S extends DAO<T>> S from(FirebaseManager manager, String id) {
		firebase = manager.get(id);
		return refreshed();
	}

	public <S extends DAO<T>> S from(FirebaseManager manager) {
		firebase = manager.get();
		return refreshed();
	}

	public <S extends DAO<T>> S from(String id) {
		return from(Firebase.manager(), id);
	}

	public <S extends DAO<T>> S ready() {
		if (firebase == null) {
			return from(Firebase.manager());
		}
		return refreshed();
	}

	public Selection selectAll() {
		ready();
		return new Selection(collection);
	}

	public Selection selectWhereEqualTo(String name, Object value) {
		name = Selection.clean(name);
		ready();
		return new Selection(collection.whereEqualTo(name, value));
	}

	public Selection selectWhereNotEqualTo(String name, Object value) {
		name = Selection.clean(name);
		ready();
		return new Selection(collection.whereNotEqualTo(name, value));
	}

	public Selection selectWhereLessThan(String name, Object value) {
		name = Selection.clean(name);
		ready();
		return new Selection(collection.whereLessThan(name, value));
	}

	public Selection selectWhereLessThanOrEqualTo(String name, Object value) {
		name = Selection.clean(name);
		ready();
		return new Selection(collection.whereLessThanOrEqualTo(name, value));
	}

	public Selection selectWhereGreaterThan(String name, Object value) {
		name = Selection.clean(name);
		ready();
		return new Selection(collection.whereGreaterThan(name, value));
	}

	public Selection selectWhereGreaterThanOrEqualTo(String name, Object value) {
		name = Selection.clean(name);
		ready();
		return new Selection(collection.whereGreaterThanOrEqualTo(name, value));
	}

	public Selection selectWhereContains(String name, Object value) {
		name = Selection.clean(name);
		ready();
		return new Selection(collection.whereArrayContains(name, value));
	}

	public Selection selectWhereContainsAny(String name, List<?> values) {
		name = Selection.clean(name, values);
		ready();
		return new Selection(collection.whereArrayContainsAny(name, values));
	}

	public Selection selectWhereIn(String name, List<?> values) {
		name = Selection.clean(name, values);
		ready();
		return new Selection(collection.whereIn(name, values));
	}

	public Selection selectWhereNotIn(String name, List<?> values) {
		name = Selection.clean(name, values);
		ready();
		return new Selection(collection.whereNotIn(name, values));
	}

	public void create(T object, Map<String, InputStream> streams, Class<? extends Adapter<T>> adapter) {
		validate(object);
		String key;
		DocumentReference document;
		if (auto) {
			if (get(keyField, object) != null) {
				throw new IllegalArgumentException("Key must be null in object");
			}
			document = collection.document();
			key = document.getId();
			set(keyField, object, key);
		} else {
			Object rawKey = get(keyField, object);
			key = convert(rawKey);
			document = collection.document(key);
			try {
				if (document.get().get().exists()) {
					throw new RequestFirestoreException("Key %s already exists in database".formatted(key));
				}
			} catch (ExecutionException exception) {
				throw new ExecutionFirestoreException(exception);
			} catch (InterruptedException exception) {
				throw new InterruptedFirestoreException(exception);
			}
		}
		if (streams != null) {
			createOrUpdate(object, streams, key);
		}
		postCreateOrUpdate(object, document, adapter);
	}

	public void create(T object, Map<String, InputStream> streams) {
		create(object, streams, null);
	}

	public void create(T object, Class<? extends Adapter<T>> adapter) {
		create(object, null, adapter);
	}

	public void create(T object) {
		create(object, null, null);
	}

	public T retrieve(Object rawKey, Class<? extends Adapter<T>> adapter) {
		String key = convert(rawKey);
		ready();
		DocumentSnapshot document;
		try {
			document = collection.document(key).get().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		if (!document.exists()) {
			return null;
		}
		Class<?> proxyType = null;
		if (adapter != null) {
			proxyType = source.compile(adapter.getName());
		}
		return postRetrieve(document, proxyType);
	}

	public T retrieve(Object rawKey) {
		return retrieve(rawKey, null);
	}

	public List<T> retrieve(Selection selection, Class<? extends Adapter<T>> adapter) {
		validate(selection);
		Class<?> proxyType = null;
		if (adapter != null) {
			proxyType = source.compile(adapter.getName());
		}
		List<T> values = new ArrayList<>();
		for (DocumentSnapshot document : selection.getDocuments()) {
			values.add(postRetrieve(document, proxyType));
		}
		return values;
	}

	public List<T> retrieve(Selection selection) {
		return retrieve(selection, null);
	}

	public void update(T object, Map<String, InputStream> streams, Class<? extends Adapter<T>> adapter) {
		validate(object);
		Object rawKey = get(keyField, object);
		String key = convert(rawKey);
		DocumentReference document = preUpdate(key);
		if (streams != null) {
			createOrUpdate(object, streams, key);
			List<String> blobPaths = new ArrayList<>();
			for (String name : fileFields.keySet()) {
				Field field = fileFields.get(name);
				if (get(field, object) == null) {
					blobPaths.add(join(key, name));
				}
			}
			delete(blobPaths);
		}
		postCreateOrUpdate(object, document, adapter);
	}

	public void update(T object, Map<String, InputStream> streams) {
		update(object, streams, null);
	}

	public void update(T object, Class<? extends Adapter<T>> adapter) {
		update(object, null, adapter);
	}

	public void update(T object) {
		update(object, null, null);
	}

	public void update(Map<String, Object> values, Map<String, InputStream> streams, Class<? extends Adapter<T>> adapter) {
		if (values == null) {
			throw new IllegalArgumentException("Field map cannot be null");
		}
		for (String name : values.keySet()) {
			Field field = null;
			for (Class<?> ancestor = type; !ancestor.equals(Object.class); ancestor = ancestor.getSuperclass()) {
				try {
					field = ancestor.getDeclaredField(name);
					break;
				} catch (NoSuchFieldException exception) {
				}
			}
			if (field == null) {
				throw new IllegalArgumentException("Field %s does not exist in class %s".formatted(name, type.getName()));
			}
		}
		ready();
		String keyName = keyField.getName();
		if (!values.containsKey(keyName)) {
			throw new IllegalArgumentException("Field %s must be in map".formatted(keyName));
		}
		Object rawKey = values.get(keyName);
		String key = convert(rawKey);
		DocumentReference document = preUpdate(key);
		if (streams != null) {
			for (String name : streams.keySet()) {
				if (!fileFields.containsKey(name)) {
					throw new IllegalArgumentException("File field %s does not exist in class %s".formatted(name, type.getName()));
				}
				if (values.containsKey(name)) {
					throw new IllegalArgumentException("File field %s cannot be in map".formatted(name));
				}
				validate(streams, name);
			}
			for (String name : streams.keySet()) {
				String url = createOrUpdate(streams, name, key);
				values.put(name, url);
			}
			List<String> blobPaths = new ArrayList<>();
			for (String name : fileFields.keySet()) {
				if (values.containsKey(name) && values.get(name) == null) {
					blobPaths.add(join(key, name));
				}
			}
			delete(blobPaths);
		}
		if (adapter != null) {
			Class<?> proxyType = source.compile(adapter.getName());
			try {
				T object = type.getConstructor().newInstance();
				Object proxy = proxyType.getDeclaredConstructor(type).newInstance(object);
				for (String name : values.keySet()) {
					String methodPrefix = name.substring(0, 1).toUpperCase();
					String methodSuffix = name.substring(1);
					String methodName = "get%s%s".formatted(methodPrefix, methodSuffix);
					try {
						Method method = proxyType.getDeclaredMethod(methodName);
						Field field = type.getDeclaredField(name);
						field.setAccessible(true);
						field.set(object, values.get(name));
						values.put(name, method.invoke(proxy));
					} catch (NoSuchMethodException exception) {
					}
				}
			} catch (NoSuchMethodException exception) {
				throw new BytecodeFirestoreException(exception);
			} catch (InvocationTargetException exception) {
				throw new BytecodeFirestoreException(exception);
			} catch (IllegalAccessException exception) {
				throw new BytecodeFirestoreException(exception);
			} catch (InstantiationException exception) {
				throw new BytecodeFirestoreException(exception);
			} catch (NoSuchFieldException exception) {
				throw new BytecodeFirestoreException(exception);
			}
		}
		try {
			document.update(values).get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
	}

	public void update(Map<String, Object> values, Map<String, InputStream> streams) {
		update(values, streams, null);
	}

	public void update(Map<String, Object> values, Class<? extends Adapter<T>> adapter) {
		update(values, null, adapter);
	}

	public void update(Map<String, Object> values) {
		update(values, null, null);
	}

	public void delete(Object rawKey) {
		String key = convert(rawKey);
		ready();
		try {
			collection.document(key).delete().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		List<String> blobPaths = new ArrayList<>();
		for (String name : fileFields.keySet()) {
			blobPaths.add(join(key, name));
		}
		delete(blobPaths);
	}

	public void delete(Selection selection) {
		validate(selection);
		WriteBatch batch = firestore.batch();
		List<String> blobPaths = new ArrayList<>();
		for (DocumentSnapshot document : selection.getDocuments()) {
			batch.delete(document.getReference());
			String key = document.getId();
			for (String name : fileFields.keySet()) {
				blobPaths.add(join(key, name));
			}
		}
		try {
			batch.commit().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		delete(blobPaths);
	}
}

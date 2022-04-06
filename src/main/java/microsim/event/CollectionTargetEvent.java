package microsim.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import microsim.exception.SimulationException;

/**
 * It is able to inform all elements within a collection about an event.
 */
public class CollectionTargetEvent extends Event {

	protected Enum<?> eventType;
	private Method methodInvoker;
	protected boolean readOnly = true;

	protected Collection<?> collection;

	/**
	 * Create a collection event using late binding method call.
	 * 
	 * @throws SimulationException
	 */
	public CollectionTargetEvent(Collection<?> elements, Class<?> objectType,
			String method, boolean readOnly) throws SimulationException {
		setForObject(elements, objectType, method, readOnly);
	}

	/** Create a collection event using early binding method call. */
	public CollectionTargetEvent(Collection<?> elements, Enum<?> actionType,
			boolean readOnly) {
		setForObject(elements, actionType, readOnly);
	}

	/**
	 * Recycling method. See SimEvent for more details.
	 * 
	 * @throws SimulationException
	 */
	public void setForObject(Collection<?> elements, Class<?> objectType,
			String method, boolean readOnly) throws SimulationException {
		collection = elements;
		eventType = null;
		this.readOnly = readOnly;

		Class<?> cl = objectType;
		while (cl != null)
			try {
				methodInvoker = cl.getDeclaredMethod(method, null);
				return;
			} catch (NoSuchMethodException e) {
				cl = cl.getSuperclass();
			} catch (SecurityException e) {
				System.out.println("Method: " + method);
				System.out.println("SimCollectionEvent -> SecurityException: " + e.getMessage());
				printStackTrace(e);
			}

		if (methodInvoker == null)
			throw new SimulationException("SimCollectionEvent didn't find method " + method);

	}

	/** Recycling method. See SimEvent for more details. */
	public void setForObject(Collection<?> elements, Enum<?> actionType, boolean readOnly) {
		collection = elements;
		eventType = actionType;
		methodInvoker = null;
		this.readOnly = readOnly;
	}

	/** Fire the event, calling each element contained into the collection. */
	public void fireEvent() {
		Collection<?> localCollection = collection;
		if (!readOnly)
			localCollection = new ArrayList<Object>(collection);

		Iterator<?> itr = localCollection.iterator();

		if (methodInvoker != null) {
			while (itr.hasNext()) {
				try {
					methodInvoker.invoke(itr.next(), null);
				} catch (InvocationTargetException e) {
					System.out.println("Object " + methodInvoker + " Method: "
							+ methodInvoker.getName());
					System.out
							.println("SimCollectionEvent.fireEvent -> InvocationTargetException: "
									+ e.getTargetException().toString());
					printStackTrace(e);
				} catch (IllegalAccessException e) {
					System.out.println("Object " + methodInvoker + " Method: "
							+ methodInvoker.getName());
					System.out
							.println("SimCollectionEvent.fireEvent -> IllegalAccessException: "
									+ e.getMessage());
					printStackTrace(e);
				}
			}
		} else {
			while (itr.hasNext()) {
				EventListener evL = (EventListener) itr.next();
				evL.onEvent(eventType);
			}
		}
	}

	private void printStackTrace(Exception e) {
		for (int i = 0; i < e.getStackTrace().length; i++)
			System.out.println(e.getStackTrace()[i].toString());
	}

}
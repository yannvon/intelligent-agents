package auction;

import logist.task.Task;
import logist.topology.Topology.City;

/**
 * Class describing an action
 *
 */
class ActionEntry {
	public ActionEntry next, prev;
	public int vehicleId; // FIXME necessary?
	public boolean pickup;
	public int time;
	public Task task;
	public int load;

	// header
	public ActionEntry(int vehicleId) {
		this.vehicleId = vehicleId;
		this.time = 0;
		this.load = 0;
	}

	// action
	public ActionEntry(Task t, boolean p) {
		this.task = t;
		this.pickup = p;
	}

	public void add(ActionEntry a) {
		a.next = this.next;
		this.next = a;
		if (a.next != null) {
			a.next.prev = a;
		}

		a.prev = this;

		a.vehicleId = vehicleId;

	}

	public double cost(City lastPos) {
		if (task == null) {
			return next == null ? 0 : next.cost(lastPos);
		}
		City nextCity = pickup ? task.pickupCity : task.deliveryCity;
		return lastPos.distanceTo(nextCity) + (next == null ? 0 : next.cost(nextCity));
	}

	public void remove() {
		// never remove header
		prev.next = next;
		if (next != null) {
			next.prev = prev;
		}
	}

	/**
	 * Update time of each action and the load of vehicles after the action
	 * 
	 * @param maxLoad
	 *            the maximum load of the vehicle
	 * @return true if the schedule is valid, false otherwise
	 */
	public boolean updateTimeAndLoad(int maxLoad) {
		if (load > maxLoad) {
			return false;
		}
		if (next != null) {
			next.time = time + 1;

			if (next.pickup) {
				next.load = load + next.task.weight;
			} else {
				next.load = load - next.task.weight;
			}
			return next.updateTimeAndLoad(maxLoad);
		}
		return load == 0;
	}

	private ActionEntry clone(ActionEntry prev) {
		ActionEntry a = new ActionEntry(task, pickup);
		a.prev = prev;
		a.time = time;
		a.vehicleId = vehicleId;
		if (next != null) {
			a.next = next.clone(a);
		}
		a.load = load;

		return a;
	}

	@Override
	public ActionEntry clone() {
		return clone(null);
	}

	public String toString() {
		String s = "->";
		if (pickup) {
			s = s + "P(" + task.id + ")";
		} else if (task != null) {
			s = s + "D(" + task.id + ")";
		}
		if (next != null) {
			s += next.toString();
		}
		return s;
	}

	public static ActionEntry[] copy(ActionEntry[] actions) {
		ActionEntry[] copy = new ActionEntry[actions.length];
		for (int i = 0; i < actions.length; i++) {
			copy[i] = actions[i].clone();
		}
		return copy;
	}

}
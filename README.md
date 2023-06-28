# FASTPUSS 🐾

A proof of concept for a fast pub-sub system.

## A non-distributed message broker

The heart of the system is
the [ThreadSafeSubscriberManager](src/main/java/io/github/googlielmo/fastpuss/ThreadSafeSubscriberManager.java).
The single-node [MessageBroker](src/main/java/io/github/googlielmo/fastpuss/MessageBroker.java),
[MessagePublisher](src/main/java/io/github/googlielmo/fastpuss/MessagePublisher.java), and
[MessageSubscriber](src/main/java/io/github/googlielmo/fastpuss/MessageSubscriber.java)
classes use this subscriber manager.

As a proof of concept, I implemented a [LocalRunner](src/main/java/io/github/googlielmo/fastpuss/LocalRunner.java)
class that runs a few hundreds threads: one broker, some publishers and a number of subscribers.
This proved useful in manual test and debugging.

You can run it directly with Maven:

  ```shell
  mvn compile exec:java -Dexec.mainClass=io.github.googlielmo.fastpuss.LocalRunner
  ```

### Design decisions

For the Subscriber Manager:

- A Map holds the subscription state: the keys are topic filters (strings) and the values are Collections of client ids.
- Client ids are strings in the form "/host:port".
- A `ConcurrentHashMap` is used, so to allow concurrent, thread-safe operations.
- The collection type chosen to hold client ids is the `ConcurrentLinkedDeque`, which offers concurrent, thread-safe
  operations with some useful properties, such as _weakly consistent_ iterators, which allow iterating over the
  collection itself even in the face of concurrent modification. This is quite useful to avoid expensive copy operations
  of (potentially) millions of elements to an immutable temporary copy, which would be necessary to avoid concurrent
  modification exceptions, had we used a regular collection. One caveat of this implementation is that `size()`
  is NOT a constant time operation and may return inaccurate results, as such we couldn't check the collection size in
  tests.

For the Broker:

- UDP is used as the transport protocol for the individual messages
- As wildcards are out-of-scope, the topic filters are in fact just the topic names
- Topic names cannot contain spaces, so that parsing messages becomes trivial (see [Message format](#message-format)
  below)

### Message format

The messages are UTF-8 strings in the format specified informally by the following EBNF grammar:

```ebnf
message     = verb, S, topic, S, body ;
verb        = 'PUB' | 'SUB' | 'MSG' ;
S           = { white space } ;
topic       = ? a valid, non-empty sequence of utf-8 characters, excluding white space ? ;
body        = ? a valid, possibly empty sequence of utf-8 characters, including white space ? ;
white space = ? white space as per regexp /\s/ ?
```

#### Message types

Messages are distinguished by their _verb_.
The following verbs are used in messages sent by clients to the broker:

- **PUB** publish a message to a topic. E.g.
  ```
  PUB topic1
  41.8874314503 12.4886930452
  ```
- **SUB** subscribe to a topic. E.g. (please note the LF after the topic name)
  ```
  SUB topic1
    
  ```

A different verb is used in messages sent by the broker to the clients:

- **MSG** represents a message published to a topic. E.g.
  ```
  MSG topic1
  41.8874314503 12.4886930452
  ```

## Extension: A distributed message broker

The [DistributedBroker](src/main/java/io/github/googlielmo/fastpuss/DistributedBroker.java) contains the implementation
for a distributed message broker.

### Design decisions

My assumptions for the distributed broker are:

- _Shared-nothing architecture_ where each node contains a copy of all the data.
- The data itself coincides with the state of the topic subscriptions.
- The number of nodes is fixed and their addresses is constant, plus they are known beforehand to each client and broker
  node via configuration.
- Each client can talk to any broker node via UDP, using the same protocol detailed above. In case of a network failure
  detected when sending a PUB or SUB message to a broker node, it is up to the client to switch to another node and
  retry.
- In case of a node crash, the node will eventually restart via some external mechanism.
- The broker nodes keep in sync with one another by communicating via TCP
- As soon as a node starts, possibly after a crash, it picks one of the other nodes randomly and asks for a fail-over
  initial ("massive") data transfer in "pull" fashion.
- Whenever a client subscribes to a topic, it will do so by sending a SUB message to one of the nodes as described for
  the non-distributed PoC. After updating its internal state, the broker will update all the other nodes, in "push"
  fashion.

When _node1_ connects to _node2_ it will send a command string, which can be "PULL" or "PUSH", followed by a newline.
_node2_ on receiving the command string will do the following:

- In case of `PULL` will transmit a full copy of its data to the requesting node.
- In case of `PUSH` will receive one subscription and update its state accordingly

The data sync protocol itself is very simple.
It consists of lines of utf-8 text in the form:

    topic-name
    client-id-1
    client-id-2
    ...
    client-id-n
    <empty line>

An empty line signals that the next non-empty line starts a new topic with the list of client ids.

### Traffic sizing

Suppose we have a cluster of _n_ broker nodes.

A SUB message sent from a client to one broker node _N(i)_ will use:

- 1 UDP datagram from client to _N(i)_
- _n - 1_ TCP PUSH messages with state update of topic + client id

Now suppose that for a particular topic, we have _k_ (with _k_ >> _n_) subscribed clients.
A PUB message sent for that topic from a client to one broker node _N(i)_ will use:

- 1 UDP datagram from client to _N(i)_
- _k_ UDP datagrams from _N(i)_ to the subscribed clients

A node fail-over in the conditions above will then transfer the full state, which we can assume to be _O_( _k_ * _t_ )
where _t_ is the max number of subscribed topics by any client and _k_ is the max number of clients subscribed to any
topic.

## Possible extensions

Data partitioning:

- Each node could in principle hold only a partition for _1/n_ of the topics.
- For redundancy, each node should have at least 2 such partitions: a primary and a secondary for backup of another
  node. Which primary and secondary partitions are associated to a specific topic can be determined by a hash function
  which would give an integer that is then reduced modulo _n_ to the node holding the primary. The next node (mod _n_)
  could be the secondary.
- The application protocol between pairs of nodes would change as the secondary partitions only should be updated when a
  new subscription is made.
- The client itself should change in order to contact exactly the node with the primary partition for a topic, not "any"
  node.
- In case of failure of one node, it is up to the clients to keep the secondary partition up to date.
- When a node is restarted it shall retrieve its primary partition from the secondary of another node, plus its own
  secondary from the primary of another node.

Dynamic addition and removal of nodes:

- The configuration is not immutable anymore, instead there is a static startup config and a dynamic one that overrides
  the first.
- Possibly one or more node discovery techniques can be employed to autoconfigure a new node based on the particular
  environment constraints (LAN, specific cloud such as AWS, Kubernetes, etc.)
- The partitioning (if implemented) needs to be independent of the number of nodes. This can be achieved by creating a
  high number of partitions which are then assigned to the current cluster nodes via a consistent hash function.
- A rebalancing operation should happen when the number of active nodes change.

# Video-Streaming Service

A Java software service, that offers an "Over the Top streaming service", develloped for the Network Service Engineering class of the Software Engineering Course at UMinho.

This service allows the distribution of video content, over a network of dinamically allocated distribuition nodes, that connect streams content servers and clients. Clients that connect to any distribution node will start receiving the currently distributed stream, by the currently available best path, accounting for network latencies, packet loss and edge failure. Only nodes with connected clients receive the stream in order to preserve network bandwith.

Final grade: 19.3 out of 20 (best of the class)

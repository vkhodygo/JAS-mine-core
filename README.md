# JAS-mine-core
JAS-mine is a Java framework that aims at providing a unique simulation tool for discrete-event simulations,
including agent-based and micro-simulation models.
With the aim to develop large-scale, data-driven models, the main architectural choice of JAS-mine is to use whenever
possible standard, open-source tools already available in the software development community.

The main value added of the platform lies in the integration with RDBMS (relational database management systems) tools
through ad-hoc micro-simulation Java libraries.

The management of input data persistence layers and simulation results is performed using standard database management
tools, and the platform takes care of the automatic translation of the relational model (which is typical of a database)
into the object-oriented simulation model, where each category of individuals or objects that populate the model is
represented by a specific class, with its own properties and methods.

JAS-mine allows to separate data representation and management, which is automatically taken care of by the simulation
engine, from the implementation of processes and behavioral algorithms, which should be the primary concern of the
modeler. This results in quicker, more robust and more transparent model building.

It has built-in utilities for communicating with an underlying relational database. In addition, the platform provides
standard tools which are frequently used both in agent-based modelling and dynamic micro-simulations, like design of
experiments (DOE), run-time monitoring and visualization with plots and graphs (GUI), I/O communication, statistical
analysis.

This repository contains the core libraries, for the gui libraries see https://github.com/jasmineRepo/JAS-mine-gui. See
https://github.com/jasmineRepo for a list of JAS-mine projects including demonstration models.

See https://jas-mine.net for more details.

## Documentation
See https://mrc-cso-sphsu.github.io/JAS-mine-core/javadoc/ for full JavaDoc.

## Licence
Original: LGPLv2
Current: under review.

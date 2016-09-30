#!/bin/bash
#
# ===========================================================================
# Description: Java configuration wrapper script for CRUSH tools.
# Author: Attila Kovacs <attila@submm.caltech.edu>
# Updated: 15 September 2015
# ===========================================================================  

# Attempt to auto configure CRUSH. This should provide optimal settings on
# most UNIX platforms (Linux, Mac OS X, BSD, and Solaris), or else set
# fail-safe defaults. Users can override settings by uncommenting or editing 
# lines further below, or preferably by adding their own persistent settings 
# in under /etc/crush2/startup/ or ~/.crush2/startup/ (e.g. in java.conf).
#if [ -L $0 ] ; then CRUSH=$(dirname $(readlink -f $0)) ; 
#else CRUSH=$(dirname $0) ; fi

source "$CRUSH/autoconf.sh"


# -----------------------------------------------------------------------------
# You may uncomment/edit settings in the section below. However, the preferred
# method to configure the Java runtime is through configuration files placed
# in /etc/crush2/startup/ or ~/.crush2/startup/ directories. For example,
# You may place entries like:
#
#   JAVA="/usr/java/latest/bin/java"
#   JVM="-server"
#   DATAMODEL="64"
#   USEMB=4000
#   EXTRAOPTS=""
#
# in ~/.crush2/startup/java.conf. (The contents of which are parsed as a bash
# script, so you may put other bash directives in there also.). The above would
# define a user runtime configuration that uses the latest Oracle java in
# 64-bit mode, allowing to use up to 4GB of ram, running the 'server' VM with
# no extra options. 
# -----------------------------------------------------------------------------

# Most computers come with a suitable version of Java (Oracle, OpenJDK/IcedTea)
# these days. Therefore, using the system default Java should work out of
# the box in most cases: 

#JAVA="java"

# Alternatively, If you experience problems with the default version of Java 
# (esp. GNU's buggy and sluggish gcj/gij), or you want to use a specific Java
# version on your system, just uncomment the JAVA setting below and modify,
# if you wish, to your preference.
# It is recommended that you use Oracle's latest Java, which is generally 
# found (on UNIX platforms) at:

#JAVA="/usr/java/latest/bin/java"

# Select the data model to be 32-bit or 64-bit. To use 64-bit model, you need
# a 64-bit OS and a 64-bit Java installation

#DATAMODEL="32"
#DATAMODEL="64"

# Choose the maximum amount of RAM (in MB) that you will allow Java to use.
# The default is to use up to 80% of the total available RAM. On 32-bit
# machines (or when DATAMODEL is set to "32") the value should remain 
# significantly below 2000, e.g. 1900. In 64-bit mode, you can specify more

#USEMB="1900"

# Chose a VM implementation (if more than one is available). For desktop
# systems the '-server' VM is recommended. For IMB java, the VM should be left
# blank (empty string). For ARM-based platforms (such as  the Raspberry Pi or 
# the NVIDIA Jetson TK1), one of the light-weight native VMs such as '-avian' 
# or '-jamvm' may work better, since the '-server' (default) VM may run in 
# interpreted mode. (On the Tegra TK1, '-jamvm' is thus about 5x faster than
# the crippled '-server', while '-avian' is not available).
# To see what VM options are available, run 'java -help'. The VM options are
# listed near the top of the resulting help screen.

#JVM="-server"
#JVM=""
#JVM="-jamvm"
#JVM="-avian"

# Any other Java options can be set in the EXTRAOPTS variable...

#EXTRAOPTS=""



# --------------------- DO NOT CHANGE BELOW THIS LINE -----------------------


# Parse startup configuration files in /etc/crush2/startup and 
# ~/.crush2/startup directories...

if [ -e /etc/crush2/startup ] ; then
  for file in /etc/crush2/startup/* ; do 
    if [ -f $file ] ; then source $file ; fi 
  done
fi

if [ -e ~/.crush2/startup ] ; then
  for file in ~/.crush2/startup/* ; do 
    if [ -f $file ] ; then source $file ; fi 
  done
fi


# Parse program-specific startup configurations if this wrapper script
# was called with at least one argument that specifies the program name.

if [ $# -ge 1 ] ; then
  if [ -e /etc/crush2/startup/$1 ] ; then
    for file in /etc/crush2/startup/$1/* ; do 
      if [ -f $file ] ; then source $file ; fi
    done
  fi

  if [ -e ~/.crush2/startup/$1 ] ; then
    for file in ~/.crush2/startup/$1/* ; do 
      if [ -f $file ] ; then source $file ; fi
    done
  fi
fi


if [[ $DEBUG == "TRUE" ]] ; then 
  echo "Pre-launch:"
  echo "  JAVA=$JAVA"
  echo "  JVM=$JVM"
  echo "  DATAMODEL=$DATAMODEL"
  echo "  USEMB=$USEMB"
  echo "  EXTRAOPTS=$EXTRAOPTS"
  echo
fi

JAVAOPTS="-d$DATAMODEL -Xmx${USEMB}M $EXTRAOPTS"
CLASSPATH="$CRUSH/crush2.jar:$CRUSH/tools.jar:$CRUSH/fits.jar:$CRUSH/jnum.jar:$CRUSH/bin"

export CRUSH

# ---------------------------- END OF SCRIPT --------------------------------
